package com.example.spa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.spa.catalog.persistence.Location;
import com.example.spa.catalog.persistence.LocationRepository;
import com.example.spa.catalog.persistence.Product;
import com.example.spa.catalog.persistence.ProductRepository;
import com.example.spa.movement.MovementType;
import com.example.spa.movement.persistence.Movement;
import com.example.spa.movement.persistence.MovementAllocation;
import com.example.spa.movement.persistence.MovementAllocationRepository;
import com.example.spa.movement.persistence.MovementRepository;
import com.example.spa.stock.persistence.Batch;
import com.example.spa.stock.persistence.BatchRepository;
import com.example.spa.stock.persistence.InventoryPosition;
import com.example.spa.stock.persistence.InventoryPositionRepository;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class LedgerTransactionIT {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired ProductRepository products;
  @Autowired LocationRepository locations;
  @Autowired InventoryPositionRepository positions;
  @Autowired BatchRepository batches;
  @Autowired MovementRepository movements;
  @Autowired MovementAllocationRepository allocations;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private Long positionId;

  @BeforeEach
  void createPosition() {
    transaction = new TransactionTemplate(transactionManager);
    positionId =
        transaction.execute(
            status -> {
              products.save(new Product("OIL", "Oil", "l"));
              locations.save(new Location("SPA", "Spa"));
              return positions
                  .save(new InventoryPosition("OIL", "SPA", LocalDate.of(2026, 9, 28)))
                  .getId();
            });
  }

  @AfterEach
  void clearDedicatedTestDatabase() {
    jdbc.execute(
        "truncate movement_allocation, movement, batch, inventory_position, product, location");
  }

  @Test
  void rollsBackMovementAndAllocationsTogether() {
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> {
                      LocalDate date = LocalDate.of(2026, 9, 28);
                      Batch batch =
                          batches.save(
                              new Batch(
                                  positionId, "B", date, date.plusDays(10), BigDecimal.ONE, "INV"));
                      Movement movement =
                          movements.save(
                              new Movement(
                                  positionId, date, MovementType.RECEIPT, BigDecimal.TEN, "D"));
                      allocations.save(
                          new MovementAllocation(
                              movement.getId(), batch.getId(), positionId, BigDecimal.TEN));
                      throw new IllegalStateException(
                          "Simulated failure after writing the allocation");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Simulated failure");
    assertThat(movements.findByDocumentNumber("D")).isEmpty();
    assertThat(jdbc.queryForObject("select count(*) from movement_allocation", Integer.class))
        .isZero();
    assertThat(jdbc.queryForObject("select count(*) from batch", Integer.class)).isZero();
    assertThat(positions.findById(positionId)).isPresent();
  }

  @Test
  void pessimisticLockBlocksAnotherConnectionUntilCommit() throws Exception {
    try (var second =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      second.setAutoCommit(false);
      transaction.executeWithoutResult(
          status -> {
            assertThat(positions.findByIdForUpdate(positionId)).isPresent();
            assertThatThrownBy(
                    () -> {
                      try (var statement = second.createStatement()) {
                        statement.execute("set local lock_timeout = '200ms'");
                        statement.executeQuery(
                            "select id from inventory_position where id = "
                                + positionId
                                + " for update");
                      }
                    })
                .isInstanceOfSatisfying(
                    SQLException.class,
                    error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
          });
      second.rollback();
      try (var statement = second.createStatement()) {
        statement.execute("set local lock_timeout = '1s'");
        try (var rows =
            statement.executeQuery(
                "select id from inventory_position where id = " + positionId + " for update")) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getLong(1)).isEqualTo(positionId);
        }
      }
      second.rollback();
    }
  }
}
