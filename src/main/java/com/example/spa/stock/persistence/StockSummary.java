package com.example.spa.stock.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface StockSummary {
  Long getPositionId();

  String getSku();

  String getName();

  String getUnit();

  String getLocation();

  String getLocationName();

  BigDecimal getCurrentStock();

  BigDecimal getAvailableStock();

  BigDecimal getConsumed();

  LocalDate getNearestExpiry();
}
