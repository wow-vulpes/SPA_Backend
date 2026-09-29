package com.example.spa;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationIT {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");

  @Test
  void appliesSingleSchemaMigrationAndIsIdempotent() {
    Flyway flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();
    assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    assertThat(flyway.migrate().migrationsExecuted).isZero();
    assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
  }
}
