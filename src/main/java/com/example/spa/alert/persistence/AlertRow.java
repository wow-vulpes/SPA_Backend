package com.example.spa.alert.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface AlertRow {
  String getSku();

  String getLocation();

  String getUnit();

  String getType();

  String getSeverity();

  BigDecimal getCurrentStock();

  BigDecimal getAvailableStock();

  BigDecimal getConsumed();

  LocalDate getLastMovementDate();

  Long getBatchId();

  String getBatchNumber();

  BigDecimal getBatchQuantity();

  LocalDate getExpiresOn();
}
