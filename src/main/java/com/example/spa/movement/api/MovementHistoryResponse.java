package com.example.spa.movement.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MovementHistoryResponse(List<Item> items, long total, int limit, int offset) {
  public MovementHistoryResponse {
    items = List.copyOf(items);
  }

  public record Item(
      Long id,
      String sku,
      String location,
      @JsonProperty("operation_date") LocalDate operationDate,
      String type,
      BigDecimal quantity,
      @JsonProperty("document_number") String documentNumber) {}
}
