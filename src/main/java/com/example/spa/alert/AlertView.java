package com.example.spa.alert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AlertView {
  private AlertView() {}

  public record Item(
      String sku,
      String location,
      String unit,
      String type,
      String severity,
      String message,
      BigDecimal currentStock,
      BigDecimal availableStock,
      BigDecimal historicalConsumption,
      BigDecimal averageDailyConsumption,
      BigDecimal daysOfStock,
      LocalDate lastMovementDate,
      Long batchId,
      String batchNumber,
      BigDecimal batchQuantity,
      LocalDate expiresOn) {}

  public record Page(
      LocalDate asOf,
      LocalDate consumptionFrom,
      LocalDate consumptionTo,
      int shortageDays,
      int expiryDays,
      int inactivityDays,
      List<Item> items,
      long total,
      int limit,
      int offset) {
    public Page {
      items = List.copyOf(items);
    }
  }
}
