package com.example.spa.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "app.alerts.lead-time-days=7",
      "app.alerts.expiry-days=10",
      "app.alerts.inactivity-days=20"
    })
@Testcontainers
@Import(AlertApiIT.FixedTime.class)
class AlertApiIT {
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);
  @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.11");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DATABASE::getJdbcUrl);
    r.add("spring.datasource.username", DATABASE::getUsername);
    r.add("spring.datasource.password", DATABASE::getPassword);
  }

  @TestConfiguration
  static class FixedTime {
    @Bean
    @Primary
    Clock testClock() {
      return Clock.fixed(Instant.parse("2026-09-28T21:30:00Z"), ZoneId.of("Europe/Moscow"));
    }
  }

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void seed() {
    jdbc.execute(
        "TRUNCATE movement_allocation, movement, batch, inventory_position, product, location RESTART IDENTITY");
    jdbc.update("INSERT INTO product VALUES ('OIL','Oil','ml'),('EMPTY','Empty','pcs')");
    jdbc.update("INSERT INTO location VALUES ('SPA','Spa'),('OTHER','Other')");
    jdbc.update(
        "INSERT INTO inventory_position(sku,location_code,registered_on) VALUES ('OIL','SPA',?),('OIL','OTHER',?),('EMPTY','SPA',?)",
        TODAY.minusDays(100),
        TODAY.minusDays(100),
        TODAY.minusDays(100));
    batch(1, "EXPIRED", TODAY.minusDays(1));
    batch(1, "TODAY", TODAY);
    batch(1, "BOUNDARY", TODAY.plusDays(10));
    batch(1, "LATER", TODAY.plusDays(11));
    batch(1, "ZERO", TODAY.minusDays(2));
    batch(2, "OTHER", TODAY.plusYears(1));
    move(1, 1, "RECEIPT", TODAY.minusDays(100), "92");
    move(1, 1, "CONSUME", TODAY.minusDays(20), "90");
    move(1, 2, "RECEIPT", TODAY.minusDays(21), "1");
    move(1, 3, "RECEIPT", TODAY.minusDays(21), "1");
    move(1, 4, "RECEIPT", TODAY.minusDays(21), "1");
    move(2, 6, "RECEIPT", TODAY.minusDays(19), "1000");
  }

  @Test
  void returnsEvidenceAndAllFourKindsAtConfiguredBoundaries() throws Exception {
    var r = get("");
    assertThat(r.status()).isEqualTo(200);
    var b = r.body();
    assertThat(b.path("as_of").asText()).isEqualTo(TODAY.toString());
    assertThat(b.path("consumption_from").asText()).isEqualTo(TODAY.minusDays(90).toString());
    assertThat(b.path("consumption_to").asText()).isEqualTo(TODAY.minusDays(1).toString());
    assertThat(b.path("shortage_days").asInt()).isEqualTo(7);
    assertThat(b.path("expiry_days").asInt()).isEqualTo(10);
    assertThat(b.path("inactivity_days").asInt()).isEqualTo(20);
    assertThat(b.path("total").asInt()).isEqualTo(5);
    assertThat(types(b))
        .containsExactly("EXPIRED", "EXPIRING", "EXPIRING", "SHORTAGE", "NO_MOVEMENT");
    var shortage = b.path("items").get(3);
    assertThat(shortage.path("severity").asText()).isEqualTo("WARNING");
    assertThat(shortage.path("historical_consumption").decimalValue()).isEqualByComparingTo("90");
    assertThat(shortage.path("current_stock").decimalValue()).isEqualByComparingTo("5");
    assertThat(shortage.path("available_stock").decimalValue()).isEqualByComparingTo("3");
    assertThat(shortage.path("average_daily_consumption").decimalValue()).isEqualByComparingTo("1");
    assertThat(shortage.path("days_of_stock").decimalValue()).isEqualByComparingTo("3");
    assertThat(shortage.path("batch_id").isNull()).isTrue();
    assertThat(shortage.path("message").asText()).contains("7", "90");
    assertThat(b.path("items").get(0).path("batch_quantity").decimalValue())
        .isEqualByComparingTo("2");
    assertThat(b.path("items").get(1).path("expires_on").asText()).isEqualTo(TODAY.toString());
    assertThat(b.path("items").get(2).path("expires_on").asText())
        .isEqualTo(TODAY.plusDays(10).toString());
    assertThat(b.path("items").get(4).path("last_movement_date").asText())
        .isEqualTo(TODAY.minusDays(20).toString());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class)).isEqualTo(6);
  }

  @Test
  void paginationCountsAlertsRatherThanPositionsAndKeepsStableOrder() throws Exception {
    var all = get("").body().path("items");
    for (int offset = 0; offset < 5; offset++) {
      var page = get("?sku=%20OIL%20&location=SPA&limit=1&offset=" + offset).body();
      assertThat(page.path("total").asInt()).isEqualTo(5);
      assertThat(page.path("items").size()).isEqualTo(1);
      assertThat(page.path("items").get(0)).isEqualTo(all.get(offset));
    }
    var beyond = get("?offset=2147483647").body();
    assertThat(beyond.path("total").asInt()).isEqualTo(5);
    assertThat(beyond.path("items").isEmpty()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"sku=UNKNOWN", "location=UNKNOWN", "location=OTHER", "sku=EMPTY"})
  void unmatchedOrHealthyPositionReturnsEmpty200(String filter) throws Exception {
    var r = get("?" + filter);
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body().path("total").asInt()).isZero();
    assertThat(r.body().path("items").isEmpty()).isTrue();
  }

  @Test
  void equalityAtShortageThresholdIsNotShortage() throws Exception {
    move(1, 4, "RETURN", TODAY, "4");
    assertThat(types(get("").body())).containsExactly("EXPIRED", "EXPIRING", "EXPIRING");
  }

  @Test
  void zeroAvailableWithPositiveDemandIsCritical() throws Exception {
    jdbc.update("UPDATE batch SET expires_on=? WHERE position_id=1", TODAY.minusDays(1));
    var rows = get("").body().path("items");
    for (var row : rows)
      if (row.path("type").asText().equals("SHORTAGE")) {
        assertThat(row.path("severity").asText()).isEqualTo("CRITICAL");
        assertThat(row.path("available_stock").decimalValue()).isZero();
        return;
      }
    throw new AssertionError("Expected shortage");
  }

  @Test
  void onlyConsumeInNinetyFullDaysContributesAndTinyDemandIsNotRoundedAway() throws Exception {
    jdbc.update("UPDATE movement SET operation_type='WRITEOFF' WHERE operation_type='CONSUME'");
    assertThat(types(get("").body())).doesNotContain("SHORTAGE");
    move(1, 4, "CONSUME", TODAY.minusDays(91), "0.000001");
    move(1, 4, "CONSUME", TODAY, "0.000001");
    assertThat(types(get("").body())).doesNotContain("SHORTAGE");
    move(1, 4, "CONSUME", TODAY.minusDays(90), "0.000001");
    jdbc.update("UPDATE batch SET expires_on=? WHERE position_id=1", TODAY.minusDays(1));
    var items = get("").body().path("items");
    for (var row : items)
      if (row.path("type").asText().equals("SHORTAGE")) {
        assertThat(row.path("historical_consumption").decimalValue())
            .isEqualByComparingTo("0.000001");
        assertThat(row.path("average_daily_consumption").decimalValue()).isZero();
        return;
      }
    throw new AssertionError("Expected tiny-demand shortage");
  }

  @Test
  void anyRecentOperationResetsInactivityAndZeroBalancesDoNotAlert() throws Exception {
    move(1, 1, "WRITEOFF", TODAY.minusDays(19), "2");
    assertThat(types(get("").body())).doesNotContain("EXPIRED", "NO_MOVEMENT");
    move(1, 2, "WRITEOFF", TODAY, "1");
    move(1, 3, "WRITEOFF", TODAY, "1");
    move(1, 4, "WRITEOFF", TODAY, "1");
    assertThat(types(get("").body())).containsExactly("SHORTAGE");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "limit=0",
        "limit=101",
        "offset=-1",
        "offset=2147483648",
        "limit=1.5",
        "limit=",
        "sku=",
        "location=%20",
        "offset=x"
      })
  void invalidFilterIs422(String query) throws Exception {
    var r = get("?" + query);
    assertThat(r.status()).isEqualTo(422);
    assertThat(r.body().path("code").asText()).isEqualTo("INVALID_ALERT_FILTER");
  }

  private void batch(int position, String number, LocalDate expiry) {
    jdbc.update(
        "INSERT INTO batch(position_id,batch_number,received_on,expires_on,unit_price,invoice_number) VALUES (?,?,?,?,1,?)",
        position,
        number,
        TODAY.minusDays(100),
        expiry,
        "INV-" + number);
  }

  @Test
  void shortageUsesRequestedDeliveryLeadTimeWithoutChangingOtherAlerts() throws Exception {
    var equal = get("?lead_time_days=3").body();
    assertThat(types(equal)).doesNotContain("SHORTAGE");
    assertThat(equal.path("lead_time_days").asInt()).isEqualTo(3);
    assertThat(equal.path("shortage_days").asInt()).isEqualTo(3);
    assertThat(types(get("?lead_time_days=4").body())).contains("SHORTAGE");
    assertThat(types(get("?lead_time_days=0").body())).doesNotContain("SHORTAGE");
    assertThat(get("?lead_time_days=-1").status()).isEqualTo(422);
    assertThat(get("?lead_time_days=3651").status()).isEqualTo(422);
    assertThat(get("?lead_time_days=1.5").status()).isEqualTo(422);
    assertThat(get("?lead_time_days=").status()).isEqualTo(422);
  }

  @Test
  void nullCharacterInFilterIs422() throws Exception {
    assertThat(get("?sku=OIL%00INVALID").status()).isEqualTo(422);
  }

  private void move(int position, int batch, String type, LocalDate date, String quantity) {
    BigDecimal amount = new BigDecimal(quantity);
    Long id =
        jdbc.queryForObject(
            "INSERT INTO movement(position_id,operation_date,operation_type,quantity,document_number) VALUES (?,?,?,?,?) RETURNING id",
            Long.class,
            position,
            date,
            type,
            amount,
            position + "-" + batch + "-" + type + "-" + date);
    jdbc.update(
        "INSERT INTO movement_allocation(movement_id,batch_id,position_id,quantity_delta) VALUES (?,?,?,?)",
        id,
        batch,
        position,
        List.of("CONSUME", "WRITEOFF").contains(type) ? amount.negate() : amount);
  }

  private List<String> types(JsonNode body) {
    var types = new ArrayList<String>();
    for (var row : body.path("items")) types.add(row.path("type").asText());
    return types;
  }

  private Reply get(String query) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/alerts" + query))
                  .timeout(Duration.ofSeconds(20))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), mapper.readTree(response.body()));
    }
  }

  private record Reply(int status, JsonNode body) {}
}
