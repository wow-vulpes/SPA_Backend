package com.example.spa.forecast.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastResponse(
    String sku,
    String location,
    @JsonProperty("as_of") LocalDate asOf,
    @JsonProperty("period_end_exclusive") LocalDate periodEndExclusive,
    @JsonProperty("horizon_days") long horizonDays,
    @JsonProperty("consumption_from") LocalDate consumptionFrom,
    @JsonProperty("consumption_to") LocalDate consumptionTo,
    @JsonProperty("historical_consumption") BigDecimal historicalConsumption,
    @JsonProperty("average_daily_consumption") BigDecimal averageDailyConsumption,
    @JsonProperty("current_stock") BigDecimal currentStock,
    @JsonProperty("available_stock") BigDecimal availableStock,
    @JsonProperty("forecast_demand") BigDecimal forecastDemand,
    @JsonProperty("safety_stock") BigDecimal safetyStock,
    @JsonProperty("reorder_point") BigDecimal reorderPoint,
    @JsonProperty("incoming_quantity") BigDecimal incomingQuantity,
    @JsonProperty("net_requirement") BigDecimal netRequirement,
    @JsonProperty("recommended_quantity") BigDecimal recommendedQuantity,
    @JsonProperty("unit_price") BigDecimal unitPrice,
    @JsonProperty("estimated_cost") BigDecimal estimatedCost,
    String currency,
    @JsonProperty("first_shortage_date") LocalDate firstShortageDate,
    @JsonProperty("recommended_order_date") LocalDate recommendedOrderDate,
    @JsonProperty("expected_arrival_date") LocalDate expectedArrivalDate,
    @JsonProperty("projected_expired_quantity") BigDecimal projectedExpiredQuantity,
    @JsonProperty("projected_unmet_demand") BigDecimal projectedUnmetDemand,
    List<String> explanation,
    List<Warning> warnings) {
  public ForecastResponse {
    explanation = List.copyOf(explanation);
    warnings = List.copyOf(warnings);
  }

  public record Warning(String code, String message) {}
}
