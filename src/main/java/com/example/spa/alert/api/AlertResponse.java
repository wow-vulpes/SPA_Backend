package com.example.spa.alert.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AlertResponse {
  private AlertResponse() {}

  public record Item(
      String sku,
      String location,
      String unit,
      String type,
      String severity,
      String message,
      @JsonProperty("current_stock") BigDecimal currentStock,
      @JsonProperty("available_stock") BigDecimal availableStock,
      @JsonProperty("historical_consumption") BigDecimal historicalConsumption,
      @JsonProperty("average_daily_consumption") BigDecimal averageDailyConsumption,
      @JsonProperty("days_of_stock") BigDecimal daysOfStock,
      @JsonProperty("last_movement_date") LocalDate lastMovementDate,
      @JsonProperty("batch_id") Long batchId,
      @JsonProperty("batch_number") String batchNumber,
      @JsonProperty("batch_quantity") BigDecimal batchQuantity,
      @JsonProperty("expires_on") LocalDate expiresOn) {}

  public record Page(
      @JsonProperty("as_of") LocalDate asOf,
      @JsonProperty("consumption_from") LocalDate consumptionFrom,
      @JsonProperty("consumption_to") LocalDate consumptionTo,
      @JsonProperty("shortage_days") int shortageDays,
      @JsonProperty("expiry_days") int expiryDays,
      @JsonProperty("inactivity_days") int inactivityDays,
      List<Item> items,
      long total,
      int limit,
      int offset) {
    public Page {
      items = List.copyOf(items);
    }
  }
}
