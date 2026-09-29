package com.example.spa.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StockMetricsTest {
  @ParameterizedTest
  @CsvSource({
    "180,704,2,352",
    "0,10,0,NULL",
    "0,0,0,NULL",
    "90,0,1,0",
    "1,1,0.011111,90",
    "0.000001,1,0,90000000"
  })
  void calculatesWithoutDividingByRoundedAverage(
      String consumed, String available, String average, String days) {
    var actual = StockMetrics.calculate(new BigDecimal(consumed), new BigDecimal(available));
    assertThat(actual.averageDailyConsumption()).isEqualByComparingTo(average);
    if (days.equals("NULL")) assertThat(actual.daysOfStock()).isNull();
    else assertThat(actual.daysOfStock()).isEqualByComparingTo(days);
  }
}
