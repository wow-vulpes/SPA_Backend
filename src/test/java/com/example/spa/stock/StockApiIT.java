package com.example.spa.stock;

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
@Import(StockApiIT.FixedTime.class)
class StockApiIT {
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
    jdbc.update(
        "INSERT INTO product VALUES ('OIL','Oil','ml'),('SOAP','Soap','g'),('EMPTY','Empty','pcs'),('UNUSED','Unused','pcs')");
    jdbc.update("INSERT INTO location VALUES ('SPA','Spa'),('OTHER','Other')");
    jdbc.update(
        "INSERT INTO inventory_position(sku,location_code,registered_on) VALUES ('OIL','SPA',?),('OIL','OTHER',?),('SOAP','SPA',?),('EMPTY','SPA',?)",
        TODAY.minusDays(100),
        TODAY.minusDays(1),
        TODAY.minusDays(1),
        TODAY);
    batch(1, "TODAY", TODAY);
    batch(1, "FUTURE", TODAY.plusDays(1));
    batch(1, "EXPIRED", TODAY.minusDays(1));
    batch(1, "EMPTY", TODAY.minusDays(50));
    batch(2, "OTHER", TODAY.plusDays(10));
    batch(3, "SOAP", TODAY.plusDays(10));
    move(1, 1, "RECEIPT", TODAY.minusDays(100), "600", false);
    move(1, 2, "RECEIPT", TODAY.minusDays(100), "400", false);
    move(1, 3, "RECEIPT", TODAY.minusDays(100), "5", false);
    move(1, 1, "CONSUME", TODAY.minusDays(91), "100", true);
    move(1, 1, "CONSUME", TODAY.minusDays(90), "90", true);
    move(1, 1, "CONSUME", TODAY.minusDays(1), "90", true);
    move(1, 1, "CONSUME", TODAY, "10", true);
    move(1, 1, "WRITEOFF", TODAY, "7", false);
    move(1, 1, "RETURN", TODAY, "3", false);
    move(1, 1, "CORRECTION", TODAY, "-2", false);
    move(2, 5, "RECEIPT", TODAY.minusDays(1), "20", false);
    move(3, 6, "RECEIPT", TODAY.minusDays(1), "1", false);
    move(3, 6, "CONSUME", TODAY.minusDays(1), "1", false);
  }

  @Test
  void computesWindowBalancesAndBusinessDateWithoutJoinMultiplication() throws Exception {
    var r = get("?sku=OIL&location=SPA");
    assertThat(r.status()).isEqualTo(200);
    var body = r.body();
    assertThat(body.path("as_of").asText()).isEqualTo(TODAY.toString());
    assertThat(body.path("consumption_from").asText()).isEqualTo(TODAY.minusDays(90).toString());
    assertThat(body.path("consumption_to").asText()).isEqualTo(TODAY.minusDays(1).toString());
    assertThat(body.path("total").asLong()).isEqualTo(1);
    var row = body.path("items").get(0);
    assertThat(row.path("current_stock").decimalValue()).isEqualByComparingTo("709");
    assertThat(row.path("available_stock").decimalValue()).isEqualByComparingTo("704");
    assertThat(row.path("average_daily_consumption").decimalValue()).isEqualByComparingTo("2");
    assertThat(row.path("days_of_stock").decimalValue()).isEqualByComparingTo("352");
    assertThat(row.path("nearest_expiry").asText()).isEqualTo(TODAY.minusDays(1).toString());
    assertThat(row.path("name").asText()).isEqualTo("Oil");
    assertThat(row.path("unit").asText()).isEqualTo("ml");
    assertThat(row.path("location_name").asText()).isEqualTo("Spa");
  }

  @Test
  void detailAgreesWithListAndIncludesZeroAndExpiredBatches() throws Exception {
    var response = get("/OIL");
    assertThat(response.status()).isEqualTo(200);
    var detail = response.body();
    assertThat(detail.path("locations").size()).isEqualTo(2);
    assertThat(detail.path("sku").asText()).isEqualTo("OIL");
    var spa = detail.path("locations").get(1);
    assertThat(spa.path("stock"))
        .isEqualTo(get("?sku=OIL&location=SPA").body().path("items").get(0));
    var batches = spa.path("batches");
    assertThat(batches.size()).isEqualTo(4);
    assertThat(batches.get(0).path("batch_number").asText()).isEqualTo("EMPTY");
    assertThat(batches.get(0).path("current_stock").decimalValue()).isZero();
    assertThat(batches.get(1).path("expired").asBoolean()).isTrue();
    assertThat(batches.get(1).path("current_stock").decimalValue()).isEqualByComparingTo("5");
    assertThat(batches.get(1).path("available_stock").decimalValue()).isZero();
    assertThat(batches.get(2).path("expired").asBoolean()).isFalse();
    assertThat(batches.get(2).path("current_stock").decimalValue()).isEqualByComparingTo("449");
    assertThat(batches.get(2).path("unit_price").decimalValue()).isEqualByComparingTo("12.3456");
    assertThat(batches.get(2).path("invoice_number").asText()).isEqualTo("INV-TODAY");
    assertThat(batches.get(2).path("received_on").asText())
        .isEqualTo(TODAY.minusDays(100).toString());
    BigDecimal physical = BigDecimal.ZERO, available = BigDecimal.ZERO;
    for (var b : batches) {
      physical = physical.add(b.path("current_stock").decimalValue());
      available = available.add(b.path("available_stock").decimalValue());
    }
    assertThat(physical).isEqualByComparingTo("709");
    assertThat(available).isEqualByComparingTo("704");
  }

  @Test
  void noDemandEmptyPositionsAndUnstockedProducts() throws Exception {
    var other = get("?location=OTHER").body().path("items").get(0);
    assertThat(other.path("average_daily_consumption").decimalValue()).isZero();
    assertThat(other.path("days_of_stock").isNull()).isTrue();
    var empty = get("/EMPTY").body().path("locations").get(0);
    assertThat(empty.path("batches").size()).isZero();
    assertThat(empty.path("stock").path("current_stock").decimalValue()).isZero();
    assertThat(empty.path("stock").path("nearest_expiry").isNull()).isTrue();
    var unused = get("/UNUSED");
    assertThat(unused.status()).isEqualTo(200);
    assertThat(unused.body().path("locations").size()).isZero();
    assertThat(get("/UNKNOWN").status()).isEqualTo(404);
    var soap = get("?sku=SOAP").body().path("items").get(0);
    assertThat(soap.path("days_of_stock").decimalValue()).isZero();
    assertThat(soap.path("nearest_expiry").isNull()).isTrue();
    assertThat(soap.path("average_daily_consumption").decimalValue())
        .isEqualByComparingTo("0.011111");
  }

  @Test
  void expiredOnlyStockRemainsPhysicalButHasZeroCoverage() throws Exception {
    jdbc.update("UPDATE batch SET expires_on=? WHERE position_id=1", TODAY.minusDays(1));
    var row = get("?sku=OIL&location=SPA").body().path("items").get(0);
    assertThat(row.path("current_stock").decimalValue()).isEqualByComparingTo("709");
    assertThat(row.path("available_stock").decimalValue()).isZero();
    assertThat(row.path("days_of_stock").decimalValue()).isZero();
  }

  @Test
  void filtersAndPaginationUsePositionsNotProducts() throws Exception {
    var all = get("").body();
    assertThat(all.path("total").asLong()).isEqualTo(4);
    assertThat(all.path("limit").asInt()).isEqualTo(50);
    assertThat(all.path("items").get(0).path("sku").asText()).isEqualTo("EMPTY");
    var page = get("?limit=2&offset=1").body();
    assertThat(page.path("items").size()).isEqualTo(2);
    assertThat(page.path("items").get(0).path("location").asText()).isEqualTo("OTHER");
    assertThat(page.path("items").get(1).path("location").asText()).isEqualTo("SPA");
    assertThat(page.path("total").asLong()).isEqualTo(4);
    assertThat(page.path("offset").asInt()).isEqualTo(1);
    var past = get("?limit=100&offset=2147483647").body();
    assertThat(past.path("items").size()).isZero();
    assertThat(past.path("total").asLong()).isEqualTo(4);
    assertThat(get("?sku=%20OIL%20").body().path("total").asLong()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"?sku=UNKNOWN", "?location=UNKNOWN", "?sku=oil", "?sku=%27%20OR%201%3D1--"})
  void unmatchedFiltersAreEmpty(String query) throws Exception {
    var result = get(query);
    assertThat(result.status()).isEqualTo(200);
    assertThat(result.body().path("total").asLong()).isZero();
    assertThat(result.body().path("items").size()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?sku=",
        "?location=%20",
        "?limit=0",
        "?limit=101",
        "?limit=",
        "?limit=1.5",
        "?offset=-1",
        "?offset=2147483648",
        "?offset=",
        "/%20"
      })
  void rejectsInvalidFilters(String query) throws Exception {
    var result = get(query);
    assertThat(result.status()).isEqualTo(422);
    assertThat(result.body().path("code").asText()).isEqualTo("INVALID_STOCK_FILTER");
  }

  @Test
  void getDoesNotMutateLedger() throws Exception {
    int before = jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class);
    get("");
    get("/OIL");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class))
        .isEqualTo(before);
  }

  private void batch(int position, String number, LocalDate expires) {
    jdbc.update(
        "INSERT INTO batch(position_id,batch_number,received_on,expires_on,unit_price,invoice_number) VALUES (?,?,?,?,?,?)",
        position,
        number,
        TODAY.minusDays(100),
        expires,
        new BigDecimal("12.3456"),
        "INV-" + number);
  }

  private void move(
      int position, int batch, String type, LocalDate date, String quantity, boolean split) {
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
    BigDecimal delta = type.equals("CONSUME") || type.equals("WRITEOFF") ? amount.negate() : amount;
    if (split) {
      delta = delta.divide(BigDecimal.TWO);
      allocation(id, 2, position, delta);
    }
    allocation(id, batch, position, delta);
  }

  private void allocation(Long movement, int batch, int position, BigDecimal delta) {
    jdbc.update(
        "INSERT INTO movement_allocation(movement_id,batch_id,position_id,quantity_delta) VALUES (?,?,?,?)",
        movement,
        batch,
        position,
        delta);
  }

  private Reply get(String suffix) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/stock" + suffix))
                  .timeout(Duration.ofSeconds(20))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), mapper.readTree(response.body()));
    }
  }

  @Test
  void receiptIsImmediatelyVisibleInBothStockEndpoints() throws Exception {
    String json =
        """
        {"sku":"UNUSED","location":"SPA","operation_date":"2026-09-29",
         "type":"receipt","quantity":3.123456,"document_number":"API-RECEIPT",
         "batch":{"number":"API-BATCH","expires_on":"2027-01-01",
                  "unit_price":7.1234,"invoice_number":"API-INVOICE"}}
        """;
    try (var client = HttpClient.newHttpClient()) {
      var response =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/movements"))
                  .timeout(Duration.ofSeconds(20))
                  .header("Content-Type", "application/json")
                  .POST(HttpRequest.BodyPublishers.ofString(json))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(201);
    }
    var row = get("?sku=UNUSED").body().path("items").get(0);
    assertThat(row.path("current_stock").decimalValue()).isEqualByComparingTo("3.123456");
    assertThat(row.path("available_stock").decimalValue()).isEqualByComparingTo("3.123456");
    var detail = get("/UNUSED").body().path("locations").get(0);
    assertThat(detail.path("stock")).isEqualTo(row);
    assertThat(detail.path("batches").get(0).path("unit_price").decimalValue())
        .isEqualByComparingTo("7.1234");
  }

  @Test
  void emptyDatabaseReturnsEmptyPage() throws Exception {
    jdbc.execute(
        "TRUNCATE movement_allocation, movement, batch, inventory_position, product, location");
    var r = get("");
    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body().path("items").size()).isZero();
    assertThat(r.body().path("total").asLong()).isZero();
  }

  private record Reply(int status, JsonNode body) {}
}
