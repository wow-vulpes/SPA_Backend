package com.example.spa.stock.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StockResponse {
  private StockResponse() {}

  public record Item(
      String sku,
      String name,
      String unit,
      String location,
      @JsonProperty("location_name") String locationName,
      @JsonProperty("current_stock") BigDecimal currentStock,
      @JsonProperty("available_stock") BigDecimal availableStock,
      @JsonProperty("average_daily_consumption") BigDecimal averageDailyConsumption,
      @JsonProperty("days_of_stock") BigDecimal daysOfStock,
      @JsonProperty("nearest_expiry") LocalDate nearestExpiry) {}

  public record Page(
      @JsonProperty("as_of") LocalDate asOf,
      @JsonProperty("consumption_from") LocalDate consumptionFrom,
      @JsonProperty("consumption_to") LocalDate consumptionTo,
      List<Item> items,
      long total,
      int limit,
      int offset) {
    public Page {
      items = List.copyOf(items);
    }
  }

  public record Batch(
      @JsonProperty("batch_id") Long batchId,
      @JsonProperty("batch_number") String batchNumber,
      @JsonProperty("received_on") LocalDate receivedOn,
      @JsonProperty("expires_on") LocalDate expiresOn,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      @JsonProperty("invoice_number") String invoiceNumber,
      @JsonProperty("current_stock") BigDecimal currentStock,
      @JsonProperty("available_stock") BigDecimal availableStock,
      boolean expired) {}

  public record Location(Item stock, List<Batch> batches) {
    public Location {
      batches = List.copyOf(batches);
    }
  }

  public record Detail(
      String sku,
      String name,
      String unit,
      @JsonProperty("as_of") LocalDate asOf,
      @JsonProperty("consumption_from") LocalDate consumptionFrom,
      @JsonProperty("consumption_to") LocalDate consumptionTo,
      List<Location> locations) {
    public Detail {
      locations = List.copyOf(locations);
    }
  }
}
