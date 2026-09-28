package com.example.spa.movement.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public record CreateMovementResponse(
    Long id,
    String sku,
    String location,
    @JsonProperty("current_stock") BigDecimal currentStock,
    @JsonProperty("available_stock") BigDecimal availableStock,
    List<AllocationResponse> allocations) {
  public CreateMovementResponse {
    allocations = List.copyOf(allocations);
  }

  public record AllocationResponse(
      @JsonProperty("batch_id") Long batchId,
      @JsonProperty("quantity_delta") BigDecimal quantityDelta) {}
}
