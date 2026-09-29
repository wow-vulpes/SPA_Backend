package com.example.spa.demo;

import com.example.spa.movement.MovementCommand;
import com.example.spa.movement.MovementService;
import com.example.spa.movement.MovementType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoDataLoader implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final MovementService movements;
  private final Clock clock;

  @Override
  @Transactional(timeout = 60)
  public void run(ApplicationArguments args) {
    // Serialize initializers and block concurrent writes until the empty-database check and seed
    // commit.
    jdbc.execute(
        "LOCK TABLE product, location, inventory_position, batch, movement, movement_allocation IN SHARE ROW EXCLUSIVE MODE");
    Boolean populated =
        jdbc.queryForObject(
            """
        SELECT EXISTS (SELECT 1 FROM product) OR EXISTS (SELECT 1 FROM location)
          OR EXISTS (SELECT 1 FROM inventory_position) OR EXISTS (SELECT 1 FROM batch)
          OR EXISTS (SELECT 1 FROM movement) OR EXISTS (SELECT 1 FROM movement_allocation)
        """,
            Boolean.class);
    if (Boolean.TRUE.equals(populated)) {
      log.info("Demo data skipped: inventory database is not empty");
      return;
    }
    LocalDate today = LocalDate.now(clock);
    jdbc.update(
        """
        INSERT INTO product(sku,name,unit) VALUES
        ('DEMO-OIL','Массажное масло','bottle'), ('DEMO-MASK','Маска для лица','pcs'),
        ('DEMO-SALT','Соль для ванн','kg'), ('DEMO-TOWEL','Одноразовое полотенце','pcs')
        """);
    jdbc.update(
        "INSERT INTO location(code,name) VALUES ('DEMO-CENTER','SPA Центр'),('DEMO-NORTH','SPA Север')");
    receipt(
        "DEMO-OIL",
        "DEMO-CENTER",
        "OIL-EARLY",
        today.minusDays(100),
        today.plusDays(10),
        "100",
        "200");
    receipt(
        "DEMO-OIL",
        "DEMO-CENTER",
        "OIL-LATE",
        today.minusDays(95),
        today.plusDays(365),
        "10",
        "250");
    for (int day = 90; day >= 1; day--) {
      move(
          "DEMO-OIL",
          "DEMO-CENTER",
          today.minusDays(day),
          MovementType.CONSUME,
          "1",
          "DEMO-OIL-CONSUME-" + day,
          null);
    }
    receipt(
        "DEMO-OIL",
        "DEMO-NORTH",
        "OIL-NORTH",
        today.minusDays(100),
        today.plusDays(365),
        "100",
        "240");
    move(
        "DEMO-OIL",
        "DEMO-NORTH",
        today.minusDays(1),
        MovementType.CONSUME,
        "1",
        "DEMO-NORTH-CONSUME",
        null);
    receipt(
        "DEMO-MASK",
        "DEMO-CENTER",
        "MASK-EXPIRED",
        today.minusDays(100),
        today.minusDays(1),
        "5",
        "80");
    receipt(
        "DEMO-SALT",
        "DEMO-CENTER",
        "SALT-IDLE",
        today.minusDays(100),
        today.plusDays(365),
        "40",
        "120");
    Long towel =
        receipt(
            "DEMO-TOWEL",
            "DEMO-CENTER",
            "TOWEL",
            today.minusDays(100),
            today.plusDays(365),
            "100",
            "15");
    move(
        "DEMO-TOWEL",
        "DEMO-CENTER",
        today.minusDays(5),
        MovementType.CONSUME,
        "10",
        "DEMO-TOWEL-CONSUME",
        null);
    move(
        "DEMO-TOWEL",
        "DEMO-CENTER",
        today.minusDays(4),
        MovementType.RETURN,
        "2",
        "DEMO-TOWEL-RETURN",
        towel);
    move(
        "DEMO-TOWEL",
        "DEMO-CENTER",
        today.minusDays(3),
        MovementType.WRITEOFF,
        "1",
        "DEMO-TOWEL-WRITEOFF",
        towel);
    move(
        "DEMO-TOWEL",
        "DEMO-CENTER",
        today.minusDays(2),
        MovementType.CORRECTION,
        "-1",
        "DEMO-TOWEL-CORRECTION",
        towel);
    log.info(
        "Demo data created for business date {}: 4 products, 2 locations, 101 movements", today);
  }

  private Long receipt(
      String sku,
      String location,
      String number,
      LocalDate date,
      LocalDate expiry,
      String quantity,
      String price) {
    var result =
        movements.post(
            new MovementCommand(
                sku,
                location,
                date,
                MovementType.RECEIPT,
                new BigDecimal(quantity),
                "DEMO-RECEIPT-" + number,
                null,
                new MovementCommand.NewBatch(
                    number, expiry, new BigDecimal(price), "DEMO-INVOICE-" + number)));
    return result.allocations().getFirst().batchId();
  }

  private void move(
      String sku,
      String location,
      LocalDate date,
      MovementType type,
      String quantity,
      String document,
      Long batchId) {
    movements.post(
        new MovementCommand(
            sku, location, date, type, new BigDecimal(quantity), document, batchId, null));
  }
}
