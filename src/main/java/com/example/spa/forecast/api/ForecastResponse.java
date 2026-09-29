package com.example.spa.forecast.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastResponse(
    String sku,
    String name,
    String unit,
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
    Explanation explanation,
    List<Warning> warnings) {
  public ForecastResponse {
    warnings = List.copyOf(warnings);
  }

  @JsonProperty("period")
  public Period period() {
    return new Period(asOf, periodEndExclusive.minusDays(1), horizonDays);
  }

  @JsonProperty("avg_daily_consumption")
  public BigDecimal avgDailyConsumption() {
    return averageDailyConsumption;
  }

  @JsonProperty("incoming_qty")
  public BigDecimal incomingQty() {
    return incomingQuantity;
  }

  @JsonProperty("recommended_purchase_qty")
  public BigDecimal recommendedPurchaseQty() {
    return recommendedQuantity;
  }

  @JsonProperty("stockout_date")
  public LocalDate stockoutDate() {
    return firstShortageDate;
  }

  public record Period(LocalDate from, LocalDate to, long days) {}

  public record Explanation(
      @JsonProperty("data_used") List<String> dataUsed,
      List<String> formulas,
      List<String> assumptions,
      @JsonProperty("as_of") LocalDate asOf) {
    public Explanation {
      dataUsed = List.copyOf(dataUsed);
      formulas = List.copyOf(formulas);
      assumptions = List.copyOf(assumptions);
    }
  }

  public record Warning(String code, String message) {
    @JsonProperty("level")
    public String level() {
      return switch (code) {
        case "CONSTANT_DEMAND_MODEL", "DELIVERY_ASSUMPTION", "DELIVERY_OUTSIDE_HORIZON" -> "info";
        default -> "warning";
      };
    }
  }
}
