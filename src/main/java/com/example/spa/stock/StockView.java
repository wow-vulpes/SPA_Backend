package com.example.spa.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class StockView {
  private StockView() {}

  public record Item(
      String sku,
      String name,
      String unit,
      String location,
      String locationName,
      BigDecimal currentStock,
      BigDecimal availableStock,
      BigDecimal averageDailyConsumption,
      BigDecimal daysOfStock,
      LocalDate nearestExpiry) {}

  public record Page(
      LocalDate asOf,
      LocalDate consumptionFrom,
      LocalDate consumptionTo,
      List<Item> items,
      long total,
      int limit,
      int offset) {
    public Page {
      items = List.copyOf(items);
    }
  }

  public record Batch(
      Long batchId,
      String batchNumber,
      LocalDate receivedOn,
      LocalDate expiresOn,
      BigDecimal unitPrice,
      String invoiceNumber,
      BigDecimal currentStock,
      BigDecimal availableStock,
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
      LocalDate asOf,
      LocalDate consumptionFrom,
      LocalDate consumptionTo,
      List<Location> locations) {
    public Detail {
      locations = List.copyOf(locations);
    }
  }
}
