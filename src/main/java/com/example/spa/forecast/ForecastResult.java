package com.example.spa.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastResult(
    String sku,
    String location,
    LocalDate asOf,
    LocalDate periodEndExclusive,
    long horizonDays,
    LocalDate consumptionFrom,
    LocalDate consumptionTo,
    BigDecimal historicalConsumption,
    BigDecimal averageDailyConsumption,
    BigDecimal currentStock,
    BigDecimal availableStock,
    BigDecimal forecastDemand,
    BigDecimal safetyStock,
    BigDecimal reorderPoint,
    BigDecimal incomingQuantity,
    BigDecimal netRequirement,
    BigDecimal recommendedQuantity,
    BigDecimal unitPrice,
    BigDecimal estimatedCost,
    String currency,
    LocalDate firstShortageDate,
    LocalDate recommendedOrderDate,
    LocalDate expectedArrivalDate,
    BigDecimal projectedExpiredQuantity,
    BigDecimal projectedUnmetDemand,
    List<String> explanation,
    List<Warning> warnings) {
  public ForecastResult {
    explanation = List.copyOf(explanation);
    warnings = List.copyOf(warnings);
  }

  public record Warning(String code, String message) {}
}
