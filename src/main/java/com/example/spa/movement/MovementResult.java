package com.example.spa.movement;

import java.math.BigDecimal;
import java.util.List;

public record MovementResult(
    Long id,
    String sku,
    String location,
    BigDecimal currentStock,
    BigDecimal availableStock,
    List<Allocation> allocations) {
  public MovementResult {
    allocations = List.copyOf(allocations);
  }
}
