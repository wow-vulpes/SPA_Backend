package com.example.spa.forecast;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(ForecastApiIT.FixedTime.class)
class ForecastApiIT {
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
    jdbc.update("INSERT INTO product VALUES ('OIL','Oil','ml'),('UNUSED','Unused','pcs')");
    jdbc.update("INSERT INTO location VALUES ('SPA','Spa'),('OTHER','Other')");
    jdbc.update(
        "INSERT INTO inventory_position(sku,location_code,registered_on) VALUES ('OIL','SPA',?),('OIL','OTHER',?)",
        TODAY.minusDays(100),
        TODAY);
    jdbc.update(
        """
      INSERT INTO batch(position_id,batch_number,received_on,expires_on,unit_price,invoice_number) VALUES
      (1,'OLD',?,?,2.125,'INV-1'),(1,'NEW',?,?,999,'INV-2'),(2,'OTHER',?,?,5000,'INV-3')
      """,
        TODAY.minusDays(100),
        TODAY.plusYears(2),
        TODAY.minusDays(95),
        TODAY.plusYears(2),
        TODAY,
        TODAY.plusYears(2));
    move(1, 1, "RECEIPT", TODAY.minusDays(100), "100");
    move(1, 2, "RECEIPT", TODAY.minusDays(95), "1");
    move(1, 1, "CONSUME", TODAY.minusDays(1), "90");
    move(1, 1, "RECEIPT", TODAY, "1");
    move(2, 3, "RECEIPT", TODAY, "50");
  }

  @Test
  void computesFromLedgerAndLatestReceiptEventAtSelectedLocation() throws Exception {
    var request = request();
    request.put("sku", " OIL ");
    var response = post(request);
    assertThat(response.status()).isEqualTo(200);
    var b = response.body();
    assertThat(b.path("sku").asText()).isEqualTo("OIL");
    assertThat(b.path("as_of").asText()).isEqualTo(TODAY.toString());
    assertThat(b.path("horizon_days").asInt()).isEqualTo(30);
    assertThat(b.path("historical_consumption").decimalValue()).isEqualByComparingTo("90");
    assertThat(b.path("average_daily_consumption").decimalValue()).isEqualByComparingTo("1");
    assertThat(b.path("available_stock").decimalValue()).isEqualByComparingTo("12");
    assertThat(b.path("forecast_demand").decimalValue()).isEqualByComparingTo("30");
    assertThat(b.path("net_requirement").decimalValue()).isEqualByComparingTo("20");
    assertThat(b.path("recommended_quantity").decimalValue()).isEqualByComparingTo("21");
    assertThat(b.path("unit_price").decimalValue()).isEqualByComparingTo("2.125");
    assertThat(b.path("estimated_cost").decimalValue()).isEqualByComparingTo("44.63");
    assertThat(b.path("currency").asText()).isEqualTo("RUB");
    assertThat(b.path("first_shortage_date").asText()).isEqualTo(TODAY.plusDays(12).toString());
    assertThat(b.path("recommended_order_date").asText()).isEqualTo(TODAY.plusDays(7).toString());
    assertThat(b.path("name").asText()).isEqualTo("Oil");
    assertThat(b.path("unit").asText()).isEqualTo("ml");
    assertThat(b.path("period").path("from").asText()).isEqualTo(TODAY.toString());
    assertThat(b.path("period").path("to").asText())
        .isEqualTo(TODAY.plusMonths(1).minusDays(1).toString());
    assertThat(b.path("period").path("days").asInt()).isEqualTo(30);
    assertThat(b.path("avg_daily_consumption")).isEqualTo(b.path("average_daily_consumption"));
    assertThat(b.path("incoming_qty")).isEqualTo(b.path("incoming_quantity"));
    assertThat(b.path("recommended_purchase_qty")).isEqualTo(b.path("recommended_quantity"));
    assertThat(b.path("stockout_date")).isEqualTo(b.path("first_shortage_date"));
    assertThat(b.path("explanation").isObject()).isTrue();
    assertThat(b.path("explanation").path("data_used").size()).isGreaterThanOrEqualTo(3);
    assertThat(b.path("explanation").path("formulas").size()).isGreaterThan(5);
    assertThat(b.path("explanation").path("assumptions").size()).isPositive();
    assertThat(b.path("explanation").path("as_of").asText()).isEqualTo(TODAY.toString());
    for (var warning : b.path("warnings")) {
      assertThat(warning.path("level").asText()).isIn("info", "warning", "critical");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class)).isEqualTo(5);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_position", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void suppliedDeliveriesAffectQuantityAndTimelineWithoutBeingStored() throws Exception {
    var r = request();
    r.put(
        "open_deliveries",
        List.of(Map.of("expected_date", TODAY.plusDays(20).toString(), "quantity", 40)));
    var b = post(r).body();
    assertThat(b.path("incoming_quantity").decimalValue()).isEqualByComparingTo("40");
    assertThat(b.path("recommended_quantity").decimalValue()).isZero();
    assertThat(b.path("first_shortage_date").asText()).isEqualTo(TODAY.plusDays(12).toString());
    assertThat(codes(b)).contains("AGGREGATE_PLAN_INSUFFICIENT", "DELIVERY_ASSUMPTION");
    assertThat(post(request()).body().path("incoming_quantity").decimalValue()).isZero();
  }

  @Test
  void noPositionHasNullPriceAndDoesNotCreatePosition() throws Exception {
    var r = request();
    r.put("sku", "UNUSED");
    var response = post(r);
    assertThat(response.status()).isEqualTo(200);
    var b = response.body();
    assertThat(b.path("unit_price").isNull()).isTrue();
    assertThat(b.path("estimated_cost").isNull()).isTrue();
    assertThat(b.path("recommended_order_date").isNull()).isTrue();
    assertThat(codes(b)).contains("PRICE_UNAVAILABLE", "NO_CONSUMPTION", "LIMITED_HISTORY");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_position", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void consumedPositionWithoutReceiptDoesNotInventPrice() throws Exception {
    jdbc.update(
        "UPDATE movement SET operation_type='CORRECTION' WHERE operation_type='RECEIPT' AND position_id=1");
    var b = post(request()).body();
    assertThat(b.path("recommended_quantity").decimalValue()).isEqualByComparingTo("21");
    assertThat(b.path("unit_price").isNull()).isTrue();
    assertThat(b.path("estimated_cost").isNull()).isTrue();
  }

  @Test
  void expiredStockIsExcludedAndFutureExpiryProducesWarning() throws Exception {
    jdbc.update("UPDATE batch SET expires_on=? WHERE id=1", TODAY.minusDays(1));
    jdbc.update("UPDATE batch SET expires_on=? WHERE id=2", TODAY);
    var b = post(request()).body();
    assertThat(b.path("current_stock").decimalValue()).isEqualByComparingTo("12");
    assertThat(b.path("available_stock").decimalValue()).isEqualByComparingTo("1");
    assertThat(codes(b)).contains("EXPIRED_STOCK", "LEAD_TIME_RISK");
    assertThat(b.path("first_shortage_date").asText()).isEqualTo(TODAY.plusDays(1).toString());
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3, 6, 12})
  void acceptsCalendarHorizons(int months) throws Exception {
    var r = request();
    r.put("horizon_months", months);
    var response = post(r);
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body().path("period_end_exclusive").asText())
        .isEqualTo(TODAY.plusMonths(months).toString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"sku", "location"})
  void unknownReferencesAre404(String field) throws Exception {
    var r = request();
    r.put(field, "UNKNOWN");
    assertThat(post(r).status()).isEqualTo(404);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "missing",
        "horizon",
        "fractional",
        "lead",
        "safety",
        "pack",
        "moq",
        "precision",
        "blank",
        "date",
        "past",
        "quantity",
        "null_delivery",
        "too_many",
        "nul_sku",
        "extreme_delivery"
      })
  void rejectsInvalidParameters(String scenario) throws Exception {
    var r = request();
    switch (scenario) {
      case "missing" -> r.remove("horizon_months");
      case "horizon" -> r.put("horizon_months", 2);
      case "fractional" -> r.put("lead_time_days", 1.9);
      case "lead" -> r.put("lead_time_days", -1);
      case "safety" -> r.put("safety_days", 366);
      case "pack" -> r.put("pack_size", 0);
      case "moq" -> r.put("minimum_order_quantity", -1);
      case "precision" -> r.put("pack_size", new BigDecimal("0.0000001"));
      case "blank" -> r.put("sku", " ");
      case "nul_sku" -> r.put("sku", "OIL\u0000INVALID");
      case "extreme_delivery" ->
          r.put(
              "open_deliveries",
              List.of(Map.of("expected_date", "+999999999-12-31", "quantity", 1)));
      case "date" ->
          r.put("open_deliveries", List.of(Map.of("expected_date", "2026-02-30", "quantity", 1)));
      case "past" ->
          r.put(
              "open_deliveries",
              List.of(Map.of("expected_date", TODAY.minusDays(1).toString(), "quantity", 1)));
      case "quantity" ->
          r.put(
              "open_deliveries", List.of(Map.of("expected_date", TODAY.toString(), "quantity", 0)));
      case "null_delivery" -> r.put("open_deliveries", java.util.Collections.singletonList(null));
      case "too_many" ->
          r.put(
              "open_deliveries",
              java.util.Collections.nCopies(
                  1001, Map.of("expected_date", TODAY.toString(), "quantity", 1)));
      default -> throw new AssertionError(scenario);
    }
    assertThat(post(r).status()).as(scenario).isEqualTo(422);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class)).isEqualTo(5);
  }

  private Map<String, Object> request() {
    var r = new HashMap<String, Object>();
    r.put("sku", "OIL");
    r.put("location", "SPA");
    r.put("horizon_months", 1);
    r.put("lead_time_days", 3);
    r.put("safety_days", 2);
    r.put("minimum_order_quantity", 0);
    r.put("pack_size", 3);
    return r;
  }

  private List<String> codes(JsonNode body) {
    var codes = new java.util.ArrayList<String>();
    for (var warning : body.path("warnings")) codes.add(warning.path("code").asText());
    return codes;
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
        type.equals("CONSUME") ? amount.negate() : amount);
  }

  private Reply post(Map<String, Object> body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/forecast"))
                  .timeout(Duration.ofSeconds(20))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), mapper.readTree(response.body()));
    }
  }

  private record Reply(int status, JsonNode body) {}
}
