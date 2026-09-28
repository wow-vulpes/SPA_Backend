package com.example.spa.stock.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface BatchBalance {
  Long getBatchId();

  String getBatchNumber();

  LocalDate getReceivedOn();

  LocalDate getExpiresOn();

  BigDecimal getUnitPrice();

  String getInvoiceNumber();

  BigDecimal getQuantity();
}
