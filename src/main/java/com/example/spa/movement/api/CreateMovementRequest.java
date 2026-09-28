package com.example.spa.movement.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateMovementRequest(
    @NotBlank @Size(max = 64) String sku,
    @NotBlank @Size(max = 64) String location,
    @JsonProperty("operation_date") @NotNull LocalDate operationDate,
    @NotBlank @Size(max = 16) String type,
    @NotNull @Digits(integer = 13, fraction = 6) BigDecimal quantity,
    @JsonProperty("document_number") @NotBlank @Size(max = 128) String documentNumber,
    @JsonProperty("batch_id") @Positive Long batchId,
    @Valid BatchRequest batch) {
  public record BatchRequest(
      @NotBlank @Size(max = 128) String number,
      @JsonProperty("expires_on") @NotNull LocalDate expiresOn,
      @JsonProperty("unit_price") @NotNull @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
      @JsonProperty("invoice_number") @NotBlank @Size(max = 128) String invoiceNumber) {}
}
