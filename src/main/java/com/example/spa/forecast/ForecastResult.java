package com.example.spa.forecast;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastResult(
    String sku,
    String name,
    String unit,
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
    Explanation explanation,
    List<Warning> warnings) {
  public ForecastResult {
    warnings = List.copyOf(warnings);
  }

  public record Warning(String code, String message) {}

  public record Explanation(
      List<String> dataUsed, List<String> formulas, List<String> assumptions, LocalDate asOf) {
    public Explanation {
      dataUsed = List.copyOf(dataUsed);
      formulas = List.copyOf(formulas);
      assumptions = List.copyOf(assumptions);
    }
  }
}
