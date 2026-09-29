package com.example.spa.forecast.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ForecastRequest(
    @NotBlank @Size(max = 64) String sku,
    @NotBlank @Size(max = 64) String location,
    @JsonProperty("horizon_months") @NotNull @Min(1) @Max(12) Integer horizonMonths,
    @JsonProperty("lead_time_days") @NotNull @Min(0) @Max(365) Integer leadTimeDays,
    @JsonProperty("safety_days") @NotNull @Min(0) @Max(365) Integer safetyDays,
    @JsonProperty("minimum_order_quantity")
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6) BigDecimal minimumOrderQuantity,
    @JsonProperty("pack_size")
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal packSize,
    @JsonProperty("open_deliveries") @Size(max = 1000) List<@NotNull @Valid Delivery> openDeliveries) {
  public ForecastRequest {
    // Keep null elements for Bean Validation to report 422 rather than failing construction.
    openDeliveries =
        openDeliveries == null
            ? List.of()
            : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(openDeliveries));
  }

  public List<Delivery> openDeliveries() {
    return java.util.Collections.unmodifiableList(openDeliveries);
  }

  public record Delivery(
      @JsonProperty("expected_date") @NotNull LocalDate expectedDate,
      @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6) BigDecimal quantity) {}
}
