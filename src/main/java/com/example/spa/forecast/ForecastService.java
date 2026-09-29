package com.example.spa.forecast;

import com.example.spa.catalog.persistence.LocationRepository;
import com.example.spa.catalog.persistence.ProductRepository;
import com.example.spa.forecast.persistence.ForecastRepository;
import com.example.spa.movement.MovementException;
import com.example.spa.stock.persistence.InventoryPositionRepository;
import com.example.spa.stock.persistence.StockReadRepository;
import com.example.spa.stock.persistence.StockRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForecastService {
  private final ProductRepository products;
  private final LocationRepository locations;
  private final InventoryPositionRepository positions;
  private final StockReadRepository summaries;
  private final StockRepository stock;
  private final ForecastRepository prices;
  private final Clock clock;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
  public ForecastResult calculate(ForecastCommand command) {
    LocalDate today = LocalDate.now(clock);
    if (!List.of(1, 3, 6, 12).contains(command.horizonMonths()))
      throw MovementException.invalid(
          "INVALID_HORIZON", "horizon_months должен быть 1, 3, 6 или 12");
    if (command.openDeliveries().stream().anyMatch(d -> d.expectedDate().isBefore(today)))
      throw MovementException.invalid(
          "PAST_DELIVERY_DATE",
          "Ожидаемая поставка не может быть в прошлом; укажите актуальную ожидаемую дату");
    products
        .findById(command.sku())
        .orElseThrow(() -> MovementException.notFound("SKU_NOT_FOUND", "Товар не найден"));
    locations
        .findById(command.location())
        .orElseThrow(() -> MovementException.notFound("LOCATION_NOT_FOUND", "Объект не найден"));
    var position = positions.findBySkuAndLocationCode(command.sku(), command.location());
    ForecastCalculator.Input input;
    if (position.isEmpty()) {
      input =
          new ForecastCalculator.Input(
              today, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), true);
    } else {
      var p = position.orElseThrow();
      var row =
          summaries
              .summaries(command.sku(), command.location(), today, today.minusDays(90), 1, 0)
              .getFirst();
      var batches =
          stock.batchBalances(p.getId()).stream()
              .map(
                  b ->
                      new ForecastCalculator.Batch(
                          b.getBatchId(), b.getReceivedOn(), b.getExpiresOn(), b.getQuantity()))
              .toList();
      input =
          new ForecastCalculator.Input(
              today,
              row.getConsumed(),
              row.getCurrentStock(),
              row.getAvailableStock(),
              prices.latestReceiptPrice(p.getId()).orElse(null),
              batches,
              p.getRegisteredOn().isAfter(today.minusDays(90)));
    }
    return ForecastCalculator.calculate(command, input);
  }
}
