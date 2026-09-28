package com.example.spa;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class MigrationIT {
  @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.11");

  @Test
  void upgradesPublishedBaselineAndKeepsExistingData() throws Exception {
    Flyway oldVersion =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .target("1")
            .load();
    assertThat(oldVersion.migrate().migrationsExecuted).isEqualTo(1);
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = connection.createStatement()) {
      sql.execute("create table upgrade_sentinel (value varchar(32) not null)");
      sql.execute("insert into upgrade_sentinel values ('keep-me')");
    }
    Flyway current =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .load();
    assertThat(current.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(current.migrate().migrationsExecuted).isZero();
    assertThat(current.validateWithResult().validationSuccessful).isTrue();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var sql = connection.createStatement();
        var rows = sql.executeQuery("select value from upgrade_sentinel")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("keep-me");
    }
  }
}
