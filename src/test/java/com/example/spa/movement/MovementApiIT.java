package com.example.spa.movement;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(MovementApiIT.FixedTime.class)
class MovementApiIT {
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
      return Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneId.of("Europe/Moscow"));
    }
  }

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void setup() {
    jdbc.execute(
        "TRUNCATE movement_allocation, movement, batch, inventory_position, product, location RESTART IDENTITY");
    jdbc.update("INSERT INTO product VALUES ('OIL','Oil','ml')");
    jdbc.update("INSERT INTO location VALUES ('SPA','Spa'),('OTHER','Other')");
  }

  @Test
  void allFiveOperationsAndFefo() throws Exception {
    var later = post(receipt("R1", "LATE", "2026-12-01", "10"));
    assertThat(later.status()).isEqualTo(201);
    long laterId = later.body().path("allocations").get(0).path("batch_id").asLong();
    var earlier = post(receipt("R2", "EARLY", "2026-09-28", "2.5"));
    long earlierId = earlier.body().path("allocations").get(0).path("batch_id").asLong();
    var consumed = post(request("consume", "C1", "3", null));
    assertThat(consumed.status()).isEqualTo(201);
    assertThat(consumed.body().path("allocations").size()).isEqualTo(2);
    assertThat(consumed.body().path("allocations").get(0).path("batch_id").asLong())
        .isEqualTo(earlierId);
    assertThat(consumed.body().path("current_stock").decimalValue()).isEqualByComparingTo("9.5");
    assertThat(post(request("return", "T1", "1", earlierId)).status()).isEqualTo(201);
    assertThat(post(request("writeoff", "W1", "0.5", earlierId)).status()).isEqualTo(201);
    assertThat(post(request("correction", "A1", "-2", laterId)).status()).isEqualTo(201);
    var result = post(request("correction", "A2", "0.25", laterId));
    assertThat(result.status()).isEqualTo(201);
    assertThat(result.body().path("current_stock").decimalValue()).isEqualByComparingTo("8.25");
    assertThat(count("movement")).isEqualTo(7);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(quantity_delta) FROM movement_allocation", BigDecimal.class))
        .isEqualByComparingTo("8.25");
  }

  @Test
  void expiredBatchCanBeWrittenOffAndReturnedButNotConsumed() throws Exception {
    var result = post(receipt("R", "OLD", "2026-09-27", "5"));
    long id = result.body().path("allocations").get(0).path("batch_id").asLong();
    assertThat(result.body().path("available_stock").decimalValue()).isEqualByComparingTo("0");
    var failure = post(request("consume", "C", "1", null));
    assertThat(failure.status()).isEqualTo(422);
    assertThat(failure.body().path("available_quantity").decimalValue()).isEqualByComparingTo("0");
    assertThat(post(request("writeoff", "W", "5", id)).status()).isEqualTo(201);
    assertThat(post(request("return", "T", "1", id)).body().path("available_stock").decimalValue())
        .isEqualByComparingTo("0");
    assertThat(count("movement")).isEqualTo(3);
  }

  @Test
  void duplicatesAndBatchMetadataAreChecked() throws Exception {
    var first = post(receipt(" R ", "B", "2026-12-01", "5"));
    assertThat(first.status()).isEqualTo(201);
    assertThat(post(receipt("R", "B", "2026-12-01", "5")).status()).isEqualTo(409);
    assertThat(post(receipt("R2", "B", "2026-12-02", "5")).status()).isEqualTo(422);
    assertThat(post(receipt("R3", "B", "2026-12-01", "5")).status()).isEqualTo(201);
    long id = first.body().path("allocations").get(0).path("batch_id").asLong();
    assertThat(post(request("receipt", "R4", "1", id)).status()).isEqualTo(201);
    assertThat(count("batch")).isEqualTo(1);
    assertThat(count("movement")).isEqualTo(3);
  }

  @Test
  void historicalOperationsAreChronologicalAndUseExpiryAtOperationDate() throws Exception {
    var r = receipt("R", "OLD", "2026-09-20", "2");
    r.put("operation_date", "2026-09-01");
    assertThat(post(r).status()).isEqualTo(201);
    var c = request("consume", "C", "1", null);
    c.put("operation_date", "2026-09-20");
    assertThat(post(c).status()).isEqualTo(201);
    c.put("document_number", "C2");
    c.put("operation_date", "2026-09-19");
    assertThat(post(c).body().path("code").asText()).isEqualTo("OUT_OF_ORDER_DATE");
    assertThat(count("movement")).isEqualTo(2);
  }

  @Test
  void failedFirstConsumptionRollsBackPositionCreation() throws Exception {
    assertThat(post(request("consume", "C", "1", null)).status()).isEqualTo(422);
    assertThat(count("inventory_position")).isZero();
    assertThat(count("movement")).isZero();
  }

  @Test
  void checksBatchOwnershipAndBatchSpecificBalance() throws Exception {
    var r = post(receipt("R", "B", "2026-12-01", "2"));
    long id = r.body().path("allocations").get(0).path("batch_id").asLong();
    post(receipt("R2", "B2", "2026-12-01", "100"));
    for (String type : List.of("writeoff", "correction")) {
      var failure = post(request(type, type, type.equals("correction") ? "-3" : "3", id));
      assertThat(failure.status()).isEqualTo(422);
      assertThat(failure.body().path("available_quantity").decimalValue())
          .isEqualByComparingTo("2");
    }
    var other = request("return", "T", "1", id);
    other.put("location", "OTHER");
    assertThat(post(other).body().path("code").asText()).isEqualTo("BATCH_POSITION_MISMATCH");
    assertThat(post(request("return", "T2", "1", 999L)).status()).isEqualTo(404);
    assertThat(count("inventory_position")).isEqualTo(1);
    assertThat(count("movement")).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "zero",
        "negative",
        "precision",
        "overflow",
        "future",
        "date",
        "type",
        "blank",
        "batch",
        "missing",
        "price"
      })
  void invalidRequestsDoNotWrite(String scenario) throws Exception {
    var r = receipt("R", "B", "2026-12-01", "1");
    switch (scenario) {
      case "zero" -> r.put("quantity", 0);
      case "negative" -> r.put("quantity", -1);
      case "precision" -> r.put("quantity", new BigDecimal("0.0000001"));
      case "overflow" -> r.put("quantity", new BigDecimal("10000000000000"));
      case "future" -> r.put("operation_date", "2026-09-29");
      case "date" -> r.put("operation_date", "2026-02-30");
      case "type" -> r.put("type", "unknown");
      case "blank" -> r.put("document_number", " ");
      case "batch" -> r.put("batch_id", 1);
      case "missing" -> r.remove("quantity");
      case "price" ->
          r.put(
              "batch",
              Map.of(
                  "number",
                  "B",
                  "expires_on",
                  "2026-12-01",
                  "unit_price",
                  -1,
                  "invoice_number",
                  "I"));
      default -> throw new AssertionError(scenario);
    }
    assertThat(post(r).status()).as(scenario).isEqualTo(422);
    assertThat(count("movement")).isZero();
    assertThat(count("batch")).isZero();
  }

  @Test
  void unknownReferencesAndMalformedJson() throws Exception {
    var r = receipt("R", "B", "2026-12-01", "1");
    r.put("sku", "UNKNOWN");
    assertThat(post(r).status()).isEqualTo(404);
    r.put("sku", "OIL");
    r.put("location", "UNKNOWN");
    assertThat(post(r).status()).isEqualTo(404);
    assertThat(send("{").status()).isEqualTo(400);
  }

  @Test
  void concurrentConsumersCannotOverdraw() throws Exception {
    post(receipt("R", "B", "2026-12-01", "10"));
    var results =
        concurrent(request("consume", "C1", "7", null), request("consume", "C2", "7", null));
    assertThat(results).extracting(Reply::status).containsExactlyInAnyOrder(201, 422);
    assertThat(count("movement")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(quantity_delta) FROM movement_allocation", BigDecimal.class))
        .isEqualByComparingTo("3");
  }

  @Test
  void concurrentFirstReceiptsSharePositionAndBatch() throws Exception {
    var results =
        concurrent(receipt("R1", "B", "2026-12-01", "2"), receipt("R2", "B", "2026-12-01", "3"));
    assertThat(results).extracting(Reply::status).containsOnly(201);
    assertThat(count("inventory_position")).isEqualTo(1);
    assertThat(count("batch")).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(quantity_delta) FROM movement_allocation", BigDecimal.class))
        .isEqualByComparingTo("5");
  }

  @Test
  void duplicateAcrossConcurrentPositionsRollsBackLoser() throws Exception {
    var other = receipt("D", "B", "2026-12-01", "3");
    other.put("location", "OTHER");
    var results = concurrent(receipt("D", "B", "2026-12-01", "2"), other);
    assertThat(results).extracting(Reply::status).containsExactlyInAnyOrder(201, 409);
    assertThat(count("movement")).isEqualTo(1);
    assertThat(count("batch")).isEqualTo(1);
    assertThat(count("inventory_position")).isEqualTo(1);
    assertThat(count("movement_allocation")).isEqualTo(1);
  }

  private List<Reply> concurrent(Map<String, Object> first, Map<String, Object> second)
      throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return post(first);
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return post(second);
              });
      start.countDown();
      return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
    }
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private Map<String, Object> request(String type, String doc, String qty, Long batch) {
    var r = new HashMap<String, Object>();
    r.put("sku", "OIL");
    r.put("location", "SPA");
    r.put("operation_date", "2026-09-28");
    r.put("type", type);
    r.put("document_number", doc);
    r.put("quantity", new BigDecimal(qty));
    if (batch != null) r.put("batch_id", batch);
    return r;
  }

  private Map<String, Object> receipt(String doc, String batch, String expiry, String qty) {
    var r = request("receipt", doc, qty, null);
    r.put(
        "batch",
        Map.of(
            "number",
            batch,
            "expires_on",
            expiry,
            "unit_price",
            new BigDecimal("12.3456"),
            "invoice_number",
            "INV"));
    return r;
  }

  private Reply post(Map<String, Object> request) throws Exception {
    return send(mapper.writeValueAsString(request));
  }

  private Reply send(String json) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/movements"))
              .timeout(Duration.ofSeconds(25))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      return new Reply(response.statusCode(), mapper.readTree(response.body()));
    }
  }

  private record Reply(int status, JsonNode body) {}
}
