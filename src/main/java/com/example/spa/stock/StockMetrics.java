package com.example.spa.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record StockMetrics(BigDecimal averageDailyConsumption, BigDecimal daysOfStock) {
  public static final int WINDOW_DAYS = 90;

  public static StockMetrics calculate(BigDecimal consumed, BigDecimal available) {
    BigDecimal days = BigDecimal.valueOf(WINDOW_DAYS);
    BigDecimal average = consumed.divide(days, 6, RoundingMode.HALF_UP);
    // Calculate coverage from the unrounded demand, including very small positive values.
    BigDecimal coverage =
        consumed.signum() == 0
            ? null
            : available.multiply(days).divide(consumed, 2, RoundingMode.HALF_UP);
    return new StockMetrics(average, coverage);
  }
}
