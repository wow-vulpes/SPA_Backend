package com.example.spa.forecast;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ForecastCalculatorTest {
  private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

  private static BigDecimal n(String value) {
    return new BigDecimal(value);
  }

  private ForecastCommand command(
      int months,
      int lead,
      int safety,
      String moq,
      String pack,
      List<ForecastCommand.Delivery> deliveries) {
    return new ForecastCommand("OIL", "SPA", months, lead, safety, n(moq), n(pack), deliveries);
  }

  private ForecastCalculator.Input input(
      LocalDate date, String consumed, String stock, LocalDate expiry, String price) {
    return new ForecastCalculator.Input(
        date,
        n(consumed),
        n(stock),
        n(stock),
        price == null ? null : n(price),
        List.of(new ForecastCalculator.Batch(1L, date.minusDays(100), expiry, n(stock))),
        false,
        "Oil",
        "ml");
  }

  @Test
  void computesDemandSafetyReorderMoqPackAndCost() {
    var result =
        ForecastCalculator.calculate(
            command(
                1,
                3,
                4,
                "60",
                "7",
                List.of(new ForecastCommand.Delivery(TODAY.plusDays(5), n("6")))),
            input(TODAY, "180", "10", TODAY.plusYears(2), "2.125"));
    assertThat(result.horizonDays()).isEqualTo(30);
    assertThat(result.averageDailyConsumption()).isEqualByComparingTo("2");
    assertThat(result.forecastDemand()).isEqualByComparingTo("60");
    assertThat(result.safetyStock()).isEqualByComparingTo("8");
    assertThat(result.reorderPoint()).isEqualByComparingTo("14");
    assertThat(result.netRequirement()).isEqualByComparingTo("52");
    assertThat(result.recommendedQuantity()).isEqualByComparingTo("63");
    assertThat(result.estimatedCost()).isEqualByComparingTo("133.88");
    assertThat(result.firstShortageDate()).isEqualTo(TODAY.plusDays(8));
    assertThat(result.recommendedOrderDate()).isEqualTo(TODAY);
    assertThat(result.expectedArrivalDate()).isEqualTo(TODAY.plusDays(3));
  }

  @ParameterizedTest
  @CsvSource({
    "2026-01-31,1,2026-02-28,28",
    "2024-01-31,1,2024-02-29,29",
    "2026-09-29,3,2026-12-29,91",
    "2026-09-29,6,2027-03-29,181",
    "2023-03-01,12,2024-03-01,366"
  })
  void usesCalendarMonths(String from, int months, String end, long days) {
    LocalDate date = LocalDate.parse(from);
    var result =
        ForecastCalculator.calculate(
            command(months, 0, 0, "0", "1", List.of()),
            input(date, "90", "1000", date.plusYears(2), "1"));
    assertThat(result.periodEndExclusive()).isEqualTo(LocalDate.parse(end));
    assertThat(result.horizonDays()).isEqualTo(days);
    assertThat(result.forecastDemand()).isEqualByComparingTo(BigDecimal.valueOf(days));
  }

  @Test
  void doesNotOrderMoqWhenNeedIsZero() {
    var r =
        ForecastCalculator.calculate(
            command(1, 1, 0, "1000", "7", List.of()),
            input(TODAY, "90", "30", TODAY.plusYears(1), "1"));
    assertThat(r.recommendedQuantity()).isZero();
    assertThat(r.firstShortageDate()).isNull();
    assertThat(r.recommendedOrderDate()).isNull();
    assertThat(r.expectedArrivalDate()).isNull();
  }

  @Test
  void noHistoryOrPriceDoesNotInventDatesOrCost() {
    var r =
        ForecastCalculator.calculate(
            command(1, 1, 0, "10", "1", List.of()), input(TODAY, "0", "0", TODAY, null));
    assertThat(r.recommendedQuantity()).isZero();
    assertThat(r.unitPrice()).isNull();
    assertThat(r.estimatedCost()).isNull();
    assertThat(r.firstShortageDate()).isNull();
    assertThat(r.recommendedOrderDate()).isNull();
    assertThat(r.warnings())
        .extracting(ForecastResult.Warning::code)
        .contains("NO_CONSUMPTION", "PRICE_UNAVAILABLE");
  }

  @Test
  void tinyDemandDoesNotDisappearBeforePackRounding() {
    var r =
        ForecastCalculator.calculate(
            command(1, 0, 0, "0", "0.000001", List.of()),
            input(TODAY, "0.000001", "0", TODAY, "0"));
    assertThat(r.averageDailyConsumption()).isZero();
    assertThat(r.recommendedQuantity()).isEqualByComparingTo("0.000001");
    assertThat(r.firstShortageDate()).isEqualTo(TODAY);
    assertThat(r.estimatedCost()).isZero();
  }

  @Test
  void expiryIsInclusiveAndRisksOverrideMisleadingAggregateSufficiency() {
    var r =
        ForecastCalculator.calculate(
            command(1, 3, 0, "0", "1", List.of()), input(TODAY, "90", "100", TODAY, "1"));
    assertThat(r.recommendedQuantity()).isZero();
    assertThat(r.projectedExpiredQuantity()).isEqualByComparingTo("99");
    assertThat(r.firstShortageDate()).isEqualTo(TODAY.plusDays(1));
    assertThat(r.projectedUnmetDemand()).isEqualByComparingTo("29");
    assertThat(r.warnings())
        .extracting(ForecastResult.Warning::code)
        .contains("EXPIRY_WITHIN_HORIZON", "AGGREGATE_PLAN_INSUFFICIENT", "LEAD_TIME_RISK");
  }

  @Test
  void lateDeliveryCannotCoverEarlierDeficitAndEndDateIsExcluded() {
    var deliveries =
        List.of(
            new ForecastCommand.Delivery(TODAY.plusDays(10), n("30")),
            new ForecastCommand.Delivery(TODAY.plusMonths(1), n("100")));
    var r =
        ForecastCalculator.calculate(
            command(1, 1, 0, "0", "1", deliveries), input(TODAY, "90", "0", TODAY, "1"));
    assertThat(r.incomingQuantity()).isEqualByComparingTo("30");
    assertThat(r.recommendedQuantity()).isZero();
    assertThat(r.firstShortageDate()).isEqualTo(TODAY);
    assertThat(r.projectedUnmetDemand()).isEqualByComparingTo("10");
    assertThat(r.warnings())
        .extracting(ForecastResult.Warning::code)
        .contains("DELIVERY_OUTSIDE_HORIZON", "AGGREGATE_PLAN_INSUFFICIENT");
  }

  @Test
  void sameDayDeliveryIsAvailableBeforeConsumption() {
    var request = command(1, 0, 0, "0", "1", List.of(new ForecastCommand.Delivery(TODAY, n("30"))));
    var r = ForecastCalculator.calculate(request, input(TODAY, "90", "0", TODAY, "1"));
    assertThat(r.firstShortageDate()).isNull();
    assertThat(r.projectedUnmetDemand()).isZero();
  }

  @Test
  void simulatesFefoBeforeLongLivedStock() {
    var in =
        new ForecastCalculator.Input(
            TODAY,
            n("90"),
            n("31"),
            n("31"),
            n("1"),
            List.of(
                new ForecastCalculator.Batch(2L, TODAY.minusDays(2), TODAY.plusYears(1), n("30")),
                new ForecastCalculator.Batch(1L, TODAY.minusDays(1), TODAY, n("1"))),
            false,
            "Oil",
            "ml");
    var r = ForecastCalculator.calculate(command(1, 0, 0, "0", "1", List.of()), in);
    assertThat(r.projectedExpiredQuantity()).isZero();
    assertThat(r.firstShortageDate()).isNull();
  }

  @Test
  void proposedOrderCanBeInFutureAndArrivesBeforeShortage() {
    var r =
        ForecastCalculator.calculate(
            command(1, 2, 1, "0", "1", List.of()),
            input(TODAY, "90", "10", TODAY.plusYears(1), "1"));
    assertThat(r.firstShortageDate()).isEqualTo(TODAY.plusDays(10));
    assertThat(r.recommendedOrderDate()).isEqualTo(TODAY.plusDays(7));
    assertThat(r.expectedArrivalDate()).isEqualTo(TODAY.plusDays(9));
  }
}
