package com.example.spa.movement;

import static org.assertj.core.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MovementHistoryApiIT {
  @Container static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17.11");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", DATABASE::getJdbcUrl);
    r.add("spring.datasource.username", DATABASE::getUsername);
    r.add("spring.datasource.password", DATABASE::getPassword);
  }

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper mapper;

  @BeforeEach
  void seed() {
    jdbc.execute(
        "TRUNCATE movement_allocation, movement, batch, inventory_position, product, location RESTART IDENTITY");
    jdbc.update("INSERT INTO product VALUES ('OIL','Oil','ml'),('SOAP','Soap','g')");
    jdbc.update("INSERT INTO location VALUES ('SPA','Spa'),('OTHER','Other')");
    jdbc.update(
        "INSERT INTO inventory_position(sku,location_code,registered_on) VALUES ('OIL','SPA','2026-01-01'),('OIL','OTHER','2026-01-01'),('SOAP','SPA','2026-01-01')");
    jdbc.update(
        """
      INSERT INTO movement(position_id,operation_date,operation_type,quantity,document_number) VALUES
      (1,'2026-01-01','RECEIPT',10,'R1'),(1,'2026-01-02','CONSUME',2.123456,'C1'),
      (1,'2026-01-02','RETURN',1,'T1'),(1,'2026-01-03','WRITEOFF',1,'W1'),
      (1,'2026-01-03','CORRECTION',-0.5,'A1'),(2,'2026-01-02','RECEIPT',7,'R2'),
      (3,'2026-01-02','RECEIPT',8,'R3')
      """);
  }

  @Test
  void returnsStableOrderAndExactFields() throws Exception {
    var response = get("");
    assertThat(response.status()).isEqualTo(200);
    var b = response.body();
    assertThat(b.path("total").asLong()).isEqualTo(7);
    assertThat(b.path("limit").asInt()).isEqualTo(50);
    assertThat(b.path("offset").asInt()).isZero();
    assertThat(ids(b)).containsExactly(5L, 4L, 7L, 6L, 3L, 2L, 1L);
    var first = b.path("items").get(0);
    assertThat(first.path("sku").asText()).isEqualTo("OIL");
    assertThat(first.path("location").asText()).isEqualTo("SPA");
    assertThat(first.path("operation_date").asText()).isEqualTo("2026-01-03");
    assertThat(first.path("type").asText()).isEqualTo("correction");
    assertThat(first.path("document_number").asText()).isEqualTo("A1");
    assertThat(first.path("quantity").decimalValue()).isEqualByComparingTo("-0.5");
    assertThat(b.path("items").get(5).path("quantity").decimalValue())
        .isEqualByComparingTo("2.123456");
  }

  @Test
  void combinesFiltersAndIncludesBothDates() throws Exception {
    var b = get("?sku=OIL&location=SPA&date_from=2026-01-02&date_to=2026-01-03").body();
    assertThat(ids(b)).containsExactly(5L, 4L, 3L, 2L);
    assertThat(b.path("total").asLong()).isEqualTo(4);
    assertThat(
            ids(
                get("?sku=OIL&location=SPA&type=CoNsUmE&date_from=2026-01-02&date_to=2026-01-02")
                    .body()))
        .containsExactly(2L);
    assertThat(ids(get("?date_from=2026-01-03").body())).containsExactly(5L, 4L);
    assertThat(ids(get("?date_to=2026-01-01").body())).containsExactly(1L);
    assertThat(ids(get("?sku=SOAP").body())).containsExactly(7L);
    assertThat(ids(get("?location=OTHER").body())).containsExactly(6L);
  }

  @ParameterizedTest
  @ValueSource(strings = {"receipt", "consume", "return", "writeoff", "correction"})
  void filtersEveryType(String type) throws Exception {
    var b = get("?type=" + type).body();
    assertThat(b.path("items").size()).isEqualTo(type.equals("receipt") ? 3 : 1);
    for (var item : b.path("items")) assertThat(item.path("type").asText()).isEqualTo(type);
  }

  @Test
  void offsetIsRowOffsetAndTotalIgnoresPagination() throws Exception {
    var b = get("?limit=2&offset=1").body();
    assertThat(ids(b)).containsExactly(4L, 7L);
    assertThat(b.path("total").asLong()).isEqualTo(7);
    assertThat(b.path("limit").asInt()).isEqualTo(2);
    assertThat(b.path("offset").asInt()).isEqualTo(1);
    assertThat(ids(get("?limit=2&offset=3").body())).containsExactly(6L, 3L);
    var past = get("?offset=2147483647").body();
    assertThat(ids(past)).isEmpty();
    assertThat(past.path("total").asLong()).isEqualTo(7);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?sku=UNKNOWN",
        "?location=UNKNOWN",
        "?sku=oil",
        "?sku=%27%20OR%201%3D1--",
        "?date_from=2099-01-01"
      })
  void unmatchedFiltersReturnEmptyPage(String query) throws Exception {
    var r = get(query);
    assertThat(r.status()).isEqualTo(200);
    assertThat(ids(r.body())).isEmpty();
    assertThat(r.body().path("total").asLong()).isZero();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "?limit=0",
        "?limit=101",
        "?limit=-1",
        "?limit=1.5",
        "?limit=abc",
        "?limit=2147483648",
        "?limit=",
        "?offset=-1",
        "?offset=1.2",
        "?offset=2147483648",
        "?offset=",
        "?type=nope",
        "?type=",
        "?sku=",
        "?location=%20",
        "?date_from=2026-02-30",
        "?date_to=not-a-date",
        "?date_from=2026-1-1",
        "?date_from=2026-01-02&date_to=2026-01-01"
      })
  void invalidFiltersAre422AndDoNotWrite(String query) throws Exception {
    var r = get(query);
    assertThat(r.status()).as(query).isEqualTo(422);
    assertThat(r.body().path("code").asText()).isEqualTo("INVALID_HISTORY_FILTER");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM movement", Integer.class)).isEqualTo(7);
  }

  @Test
  void splitMovementIsReturnedOnce() throws Exception {
    jdbc.update(
        """
      INSERT INTO batch(position_id,batch_number,received_on,expires_on,unit_price,invoice_number)
      VALUES (1,'B1','2026-01-01','2027-01-01',1,'I1'),(1,'B2','2026-01-01','2027-01-01',1,'I2')
      """);
    jdbc.update(
        """
      INSERT INTO movement_allocation(movement_id,batch_id,position_id,quantity_delta)
      VALUES (1,1,1,5),(1,2,1,5),(2,1,1,-1),(2,2,1,-1.123456)
      """);
    var r = get("?type=consume");
    assertThat(r.status()).isEqualTo(200);
    assertThat(ids(r.body())).containsExactly(2L);
    assertThat(r.body().path("total").asLong()).isEqualTo(1);
  }

  @Test
  void emptyDatabaseAndMaximumPageSize() throws Exception {
    jdbc.update("DELETE FROM movement");
    var r = get("?limit=100");
    assertThat(r.status()).isEqualTo(200);
    assertThat(ids(r.body())).isEmpty();
    assertThat(r.body().path("total").asLong()).isZero();
  }

  private java.util.List<Long> ids(JsonNode page) {
    var ids = new java.util.ArrayList<Long>();
    for (var item : page.path("items")) ids.add(item.path("id").asLong());
    return ids;
  }

  private Reply get(String query) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var r =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("http://localhost:" + port + "/api/movements" + query))
                  .timeout(Duration.ofSeconds(20))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return new Reply(r.statusCode(), mapper.readTree(r.body()));
    }
  }

  private record Reply(int status, JsonNode body) {}
}
