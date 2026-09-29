package com.example.spa.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastCommand(
    String sku,
    String location,
    int horizonMonths,
    int leadTimeDays,
    int safetyDays,
    BigDecimal minimumOrderQuantity,
    BigDecimal packSize,
    List<Delivery> openDeliveries) {
  public ForecastCommand {
    sku = sku == null ? null : sku.strip();
    location = location == null ? null : location.strip();
    openDeliveries = openDeliveries == null ? List.of() : List.copyOf(openDeliveries);
  }

  public record Delivery(LocalDate expectedDate, BigDecimal quantity) {}
}
