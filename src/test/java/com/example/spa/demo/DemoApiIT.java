package com.example.spa.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    properties = "app.demo.enabled=true")
@Testcontainers
@Import(DemoApiIT.FixedTime.class)
class DemoApiIT {
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
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-09-28T21:30:00Z"), ZoneId.of("Europe/Moscow"));
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired DemoDataLoader loader;
  @Autowired ObjectMapper mapper;

  @Value("${local.server.port}")
  int port;

  @BeforeEach
  void reset() {
    clear();
    loader.run(null);
  }

  @Test
  void completeScenarioFromSeedToForecastFefoAndDuplicateProtection() throws Exception {
    assertThat(count("movement")).isEqualTo(101);
    assertThat(count("batch")).isEqualTo(6);
    assertThat(count("inventory_position")).isEqualTo(5);
    loader.run(null);
    assertThat(count("movement")).isEqualTo(101);
    var stock =
        request("/api/stock?sku=DEMO-OIL&location=DEMO-CENTER", null).body().path("items").get(0);
    assertThat(stock.path("available_stock").decimalValue()).isEqualByComparingTo("20");
    assertThat(stock.path("average_daily_consumption").decimalValue()).isEqualByComparingTo("1");
    var detail = request("/api/stock/DEMO-OIL", null).body();
    assertThat(detail.path("locations").size()).isEqualTo(2);
    var alertTypes = new HashSet<String>();
    var alerts = request("/api/alerts", null).body();
    for (var row : alerts.path("items")) alertTypes.add(row.path("type").asText());
    assertThat(alertTypes)
        .containsExactlyInAnyOrder("SHORTAGE", "EXPIRED", "EXPIRING", "NO_MOVEMENT");
    assertThat(alerts.path("total").asInt()).isEqualTo(5);
    assertThat(
            request("/api/movements?sku=DEMO-OIL&location=DEMO-CENTER&type=consume", null)
                .body()
                .path("total")
                .asInt())
        .isEqualTo(90);
    var forecast =
        request(
            "/api/forecast",
            """
        {"sku":"DEMO-OIL","location":"DEMO-CENTER","horizon_months":1,
        "lead_time_days":3,"safety_days":5,"minimum_order_quantity":0,"pack_size":10}
        """);
    assertThat(forecast.status()).isEqualTo(200);
    assertThat(forecast.body().path("recommended_quantity").decimalValue())
        .isEqualByComparingTo("20");
    assertThat(forecast.body().path("estimated_cost").decimalValue()).isEqualByComparingTo("5000");
    assertThat(count("movement")).isEqualTo(101);
    String consume =
        """
        {"sku":"DEMO-OIL","location":"DEMO-CENTER","operation_date":"2026-09-29",
        "type":"consume","quantity":11,"document_number":"DEMO-API-CONSUME"}
        """;
    var created = request("/api/movements", consume);
    assertThat(created.status()).isEqualTo(201);
    assertThat(created.body().path("allocations").size()).isEqualTo(2);
    assertThat(created.body().path("allocations").get(0).path("quantity_delta").decimalValue())
        .isEqualByComparingTo("-10");
    assertThat(created.body().path("allocations").get(1).path("quantity_delta").decimalValue())
        .isEqualByComparingTo("-1");
    assertThat(request("/api/movements", consume).status()).isEqualTo(409);
    assertThat(
            request(
                    "/api/movements",
                    consume.replace(":11", ":1000").replace("DEMO-API-CONSUME", "DEMO-TOO-MUCH"))
                .status())
        .isEqualTo(422);
    loader.run(null);
    assertThat(count("movement")).isEqualTo(102);
    assertThat(
            request("/api/stock?sku=DEMO-OIL&location=DEMO-CENTER", null)
                .body()
                .path("items")
                .get(0)
                .path("available_stock")
                .decimalValue())
        .isEqualByComparingTo("9");
  }

  @Test
  void skipsNonemptyDatabaseWithoutAddingOrChangingData() {
    clear();
    jdbc.update("INSERT INTO product VALUES ('USER','User product','pcs')");
    loader.run(null);
    assertThat(count("product")).isEqualTo(1);
    assertThat(count("location")).isZero();
    assertThat(count("movement")).isZero();
    assertThat(jdbc.queryForObject("SELECT name FROM product WHERE sku='USER'", String.class))
        .isEqualTo("User product");
  }

  @Test
  void readinessAndReadFiltersHaveControlledHttpStatuses() throws Exception {
    assertThat(request("/actuator/health/readiness", null).status()).isEqualTo(200);
    assertThat(request("/api/stock?sku=DEMO%00INVALID", null).status()).isEqualTo(422);
    assertThat(request("/api/movements?location=DEMO%00INVALID", null).status()).isEqualTo(422);
    assertThat(request("/api/movements?date_from=0000-01-01", null).status()).isEqualTo(422);
    try (var client = HttpClient.newHttpClient()) {
      var r =
          client.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/stock"))
                  .timeout(Duration.ofSeconds(15))
                  .header("Accept", "text/plain")
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(r.statusCode()).isEqualTo(406);
    }
  }

  @Test
  void failureLateInSeedRollsBackAllDemoData() {
    clear();
    jdbc.execute(
        """
        CREATE FUNCTION reject_demo_towel() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN IF NEW.document_number='DEMO-TOWEL-CORRECTION' THEN
        RAISE EXCEPTION 'test failure'; END IF; RETURN NEW; END $$
        """);
    jdbc.execute(
        "CREATE TRIGGER reject_demo BEFORE INSERT ON movement FOR EACH ROW EXECUTE FUNCTION reject_demo_towel()");
    try {
      assertThatThrownBy(() -> loader.run(null)).isInstanceOf(RuntimeException.class);
      assertThat(count("product")).isZero();
      assertThat(count("location")).isZero();
      assertThat(count("inventory_position")).isZero();
      assertThat(count("batch")).isZero();
      assertThat(count("movement")).isZero();
      assertThat(count("movement_allocation")).isZero();
    } finally {
      jdbc.execute("DROP TRIGGER reject_demo ON movement");
      jdbc.execute("DROP FUNCTION reject_demo_towel()");
    }
    loader.run(null);
    assertThat(count("movement")).isEqualTo(101);
  }

  private int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private void clear() {
    jdbc.execute(
        "TRUNCATE movement_allocation,movement,batch,inventory_position,product,location RESTART IDENTITY");
  }

  private Reply request(String path, String body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var builder =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .timeout(Duration.ofSeconds(15));
      if (body == null) builder.GET();
      else
        builder
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
      var r = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      return new Reply(r.statusCode(), mapper.readTree(r.body()));
    }
  }

  private record Reply(int status, JsonNode body) {}
}
