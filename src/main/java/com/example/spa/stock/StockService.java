package com.example.spa.stock;

import com.example.spa.catalog.persistence.ProductRepository;
import com.example.spa.movement.MovementException;
import com.example.spa.stock.persistence.PositionBatchBalance;
import com.example.spa.stock.persistence.StockReadRepository;
import com.example.spa.stock.persistence.StockSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
public class StockService {
  private final StockReadRepository repository;
  private final ProductRepository products;
  private final Clock clock;

  public StockView.Page list(StockFilter filter) {
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(StockMetrics.WINDOW_DAYS);
    long total = repository.countPositions(filter.sku(), filter.location());
    var items =
        repository
            .summaries(
                filter.sku(), filter.location(), today, from, filter.limit(), filter.offset())
            .stream()
            .map(this::item)
            .toList();
    return new StockView.Page(
        today, from, today.minusDays(1), items, total, filter.limit(), filter.offset());
  }

  public StockView.Detail detail(String rawSku) {
    String sku = StockFilter.identifier(rawSku);
    var product =
        products
            .findById(sku)
            .orElseThrow(() -> MovementException.notFound("SKU_NOT_FOUND", "Товар не найден"));
    LocalDate today = LocalDate.now(clock);
    LocalDate from = today.minusDays(StockMetrics.WINDOW_DAYS);
    var summaries = repository.summaries(sku, null, today, from, Integer.MAX_VALUE, 0);
    var byPosition =
        repository.batches(sku).stream()
            .collect(Collectors.groupingBy(PositionBatchBalance::getPositionId));
    var locations =
        summaries.stream()
            .map(
                s ->
                    new StockView.Location(
                        item(s),
                        byPosition.getOrDefault(s.getPositionId(), List.of()).stream()
                            .map(b -> batch(b, today))
                            .toList()))
            .toList();
    return new StockView.Detail(
        sku, product.getName(), product.getUnit(), today, from, today.minusDays(1), locations);
  }

  private StockView.Item item(StockSummary row) {
    var metrics = StockMetrics.calculate(row.getConsumed(), row.getAvailableStock());
    return new StockView.Item(
        row.getSku(),
        row.getName(),
        row.getUnit(),
        row.getLocation(),
        row.getLocationName(),
        row.getCurrentStock(),
        row.getAvailableStock(),
        metrics.averageDailyConsumption(),
        metrics.daysOfStock(),
        row.getNearestExpiry());
  }

  private StockView.Batch batch(PositionBatchBalance row, LocalDate today) {
    boolean expired = row.getExpiresOn().isBefore(today);
    return new StockView.Batch(
        row.getBatchId(),
        row.getBatchNumber(),
        row.getReceivedOn(),
        row.getExpiresOn(),
        row.getUnitPrice(),
        row.getInvoiceNumber(),
        row.getQuantity(),
        expired ? BigDecimal.ZERO : row.getQuantity(),
        expired);
  }
}
