package com.example.spa.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    int status,
    String code,
    String message,
    @JsonProperty("available_quantity") BigDecimal availableQuantity,
    List<FieldViolation> violations) {
  public ApiError {
    violations = List.copyOf(violations);
  }

  public record FieldViolation(String field, String message) {}
}
