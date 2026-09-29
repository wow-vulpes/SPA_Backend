package com.example.spa.alert;

import com.example.spa.alert.persistence.AlertRepository;
import com.example.spa.alert.persistence.AlertRow;
import com.example.spa.stock.StockMetrics;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class AlertService {
  private final AlertRepository repository;
  private final AlertProperties properties;
  private final Clock clock;

  public AlertView.Page list(AlertFilter filter) {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(StockMetrics.WINDOW_DAYS);
    LocalDate inactiveThrough = today.minusDays(properties.inactivityDays());
    LocalDate expiryThrough = today.plusDays(properties.expiryDays());
    int leadTimeDays =
        filter.leadTimeDays() == null ? properties.leadTimeDays() : filter.leadTimeDays();
    long total =
        repository.countAlerts(
            filter.sku(),
            filter.location(),
            today,
            from,
            leadTimeDays,
            inactiveThrough,
            expiryThrough);
    var items =
        repository
            .alerts(
                filter.sku(),
                filter.location(),
                today,
                from,
                leadTimeDays,
                inactiveThrough,
                expiryThrough,
                filter.limit(),
                filter.offset())
            .stream()
            .map(row -> item(row, leadTimeDays))
            .toList();
    return new AlertView.Page(
        today,
        from,
        today.minusDays(1),
        leadTimeDays,
        properties.expiryDays(),
        properties.inactivityDays(),
        items,
        total,
        filter.limit(),
        filter.offset());
  }

  private AlertView.Item item(AlertRow row, int leadTimeDays) {
    var metrics = StockMetrics.calculate(row.getConsumed(), row.getAvailableStock());
    String message =
        switch (row.getType()) {
          case "SHORTAGE" ->
              "Запаса меньше, чем срок поставки "
                  + leadTimeDays
                  + " дней, при среднем расходе за 90 полных дней";
          case "EXPIRED" ->
              "Положительный остаток партии с истёкшим сроком годности; требуется проверить списание";
          case "EXPIRING" ->
              "Срок партии истекает не позднее чем через " + properties.expiryDays() + " дней";
          case "NO_MOVEMENT" ->
              "Есть физический остаток, но движений не было не менее "
                  + properties.inactivityDays()
                  + " дней";
          default -> throw new IllegalStateException("Unknown alert type: " + row.getType());
        };
    return new AlertView.Item(
        row.getSku(),
        row.getLocation(),
        row.getUnit(),
        row.getType(),
        row.getSeverity(),
        message,
        row.getCurrentStock(),
        row.getAvailableStock(),
        row.getConsumed(),
        metrics.averageDailyConsumption(),
        metrics.daysOfStock(),
        row.getLastMovementDate(),
        row.getBatchId(),
        row.getBatchNumber(),
        row.getBatchQuantity(),
        row.getExpiresOn());
  }
}
