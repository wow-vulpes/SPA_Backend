package com.example.spa.movement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovementCommand(
    String sku,
    String location,
    LocalDate operationDate,
    MovementType type,
    BigDecimal quantity,
    String documentNumber,
    Long batchId,
    NewBatch batch) {
  public MovementCommand {
    sku = normalize(sku);
    location = normalize(location);
    documentNumber = normalize(documentNumber);
  }

  public record NewBatch(
      String number, LocalDate expiresOn, BigDecimal unitPrice, String invoiceNumber) {
    public NewBatch {
      number = normalize(number);
      invoiceNumber = normalize(invoiceNumber);
    }
  }

  private static String normalize(String value) {
    return value == null ? null : value.strip();
  }
}
