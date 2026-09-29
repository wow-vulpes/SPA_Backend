package com.example.spa.forecast;

import com.example.spa.forecast.ForecastResult.Warning;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ForecastCalculator {
  private static final BigDecimal WINDOW = BigDecimal.valueOf(90);

  private ForecastCalculator() {}

  public record Batch(Long id, LocalDate receivedOn, LocalDate expiresOn, BigDecimal quantity) {}

  public record Input(
      LocalDate today,
      BigDecimal consumed,
      BigDecimal physical,
      BigDecimal available,
      BigDecimal price,
      List<Batch> batches,
      boolean limitedHistory,
      String name,
      String unit) {
    public Input {
      batches = List.copyOf(batches);
    }
  }

  public static ForecastResult calculate(ForecastCommand command, Input input) {
    LocalDate today = input.today(), end = today.plusMonths(command.horizonMonths());
    long days = ChronoUnit.DAYS.between(today, end);
    BigDecimal incoming =
        command.openDeliveries().stream()
            .filter(d -> d.expectedDate().isBefore(end))
            .map(ForecastCommand.Delivery::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal demandNumerator = input.consumed().multiply(BigDecimal.valueOf(days));
    BigDecimal safetyNumerator =
        input.consumed().multiply(BigDecimal.valueOf(command.safetyDays()));
    BigDecimal reorderNumerator =
        input
            .consumed()
            .multiply(BigDecimal.valueOf((long) command.leadTimeDays() + command.safetyDays()));
    BigDecimal needNumerator =
        demandNumerator
            .add(safetyNumerator)
            .subtract(input.available().add(incoming).multiply(WINDOW))
            .max(BigDecimal.ZERO);
    // Keep the 90-day denominator until pack rounding; no rounded average enters this decision.
    BigDecimal quantity =
        needNumerator.signum() == 0
            ? BigDecimal.ZERO
            : needNumerator
                .max(command.minimumOrderQuantity().multiply(WINDOW))
                .divide(command.packSize().multiply(WINDOW), 0, RoundingMode.CEILING)
                .multiply(command.packSize());
    var simulation = simulate(command, input, end, reorderNumerator);
    List<Warning> warnings = new ArrayList<>();
    warnings.add(
        new Warning(
            "CONSTANT_DEMAND_MODEL",
            "Расчёт использует постоянный средний расход без сезонности и плановой загрузки SPA"));
    if (input.limitedHistory())
      warnings.add(
          new Warning(
              "LIMITED_HISTORY",
              "Позиция зарегистрирована менее 90 дней назад или ещё не создана; среднее может быть занижено"));
    if (input.consumed().signum() == 0)
      warnings.add(
          new Warning(
              "NO_CONSUMPTION",
              "За 90 полных дней нет расхода; потребность и дата заказа по этой модели не определяют реальный будущий спрос"));
    if (input.price() == null)
      warnings.add(
          new Warning(
              "PRICE_UNAVAILABLE",
              "Нет поступления с ценой на выбранном объекте; стоимость неизвестна"));
    if (input.physical().compareTo(input.available()) > 0)
      warnings.add(
          new Warning("EXPIRED_STOCK", "Просроченный физический остаток исключён из доступного"));
    if (incoming.signum() > 0)
      warnings.add(
          new Warning(
              "DELIVERY_ASSUMPTION",
              "Открытые поставки взяты из запроса; предполагается своевременное поступление и годность до конца горизонта"));
    if (command.openDeliveries().stream().anyMatch(d -> !d.expectedDate().isBefore(end)))
      warnings.add(
          new Warning(
              "DELIVERY_OUTSIDE_HORIZON",
              "Поставки на дату окончания горизонта и позже не учтены"));
    if (simulation.expired().signum() > 0)
      warnings.add(
          new Warning(
              "EXPIRY_WITHIN_HORIZON",
              "Часть запаса истечёт до использования; агрегатная формула закупки не компенсирует это автоматически"));
    if (simulation.shortage() != null)
      warnings.add(
          new Warning(
              "PROJECTED_SHORTAGE",
              "Обнаружен дефицит с учётом дат поставок и годности партий; прогноз не включает рекомендованный новый заказ"));
    if (quantity.signum() == 0 && simulation.shortage() != null)
      warnings.add(
          new Warning(
              "AGGREGATE_PLAN_INSUFFICIENT",
              "По общей формуле закупка равна нулю, но календарный расчёт выявил дефицит; требуется пересмотр сроков или объёма"));
    LocalDate order = null;
    if (quantity.signum() > 0) {
      order = simulation.reorderDate();
      if (simulation.shortage() != null) {
        LocalDate latest = simulation.shortage().minusDays(command.leadTimeDays());
        if (order == null || latest.isBefore(order)) order = latest;
      }
      if (order != null && order.isBefore(today)) order = today;
      if (order == null)
        warnings.add(
            new Warning(
                "ORDER_DATE_UNAVAILABLE", "Внутри горизонта не найдена точка размещения заказа"));
    }
    LocalDate arrival = order == null ? null : order.plusDays(command.leadTimeDays());
    if (simulation.shortage() != null
        && today.plusDays(command.leadTimeDays()).isAfter(simulation.shortage()))
      warnings.add(
          new Warning(
              "LEAD_TIME_RISK",
              "Новый заказ, размещённый сегодня, не успевает к первому дефициту"));
    List<String> formulas =
        List.of(
            "Средний расход = "
                + input.consumed().toPlainString()
                + " / 90; сегодня в историю не входит",
            "Прогноз = средний расход × дни; страховой запас = средний расход × "
                + command.safetyDays(),
            "Точка заказа = средний расход × ("
                + command.leadTimeDays()
                + " + "
                + command.safetyDays()
                + ")",
            "Потребность = max(0, прогноз + страховой запас − доступный остаток − поставки в горизонте)",
            "При положительной потребности закупка = ceil(max(потребность, "
                + command.minimumOrderQuantity().toPlainString()
                + ") / "
                + command.packSize().toPlainString()
                + ") × "
                + command.packSize().toPlainString(),
            "Стоимость = рекомендуемый объём × цена последнего поступления; округление HALF_UP до 2 знаков",
            "Дата заказа: первая календарная точка заказа, не позже дефицит − срок поставки, не раньше сегодня");
    List<String> assumptions = new ArrayList<>();
    assumptions.add(
        "Цена взята из последнего RECEIPT выбранного SKU/объекта, по дате и ID движения; при отсутствии поступления неизвестна");
    assumptions.add(
        "Поставки доступны с начала дня. Объём — агрегатная рекомендация; календарная FEFO-симуляция проверяет риски без нового заказа и не оптимизирует график закупок");
    warnings.stream().map(Warning::message).forEach(assumptions::add);
    var explanation =
        new ForecastResult.Explanation(
            List.of(
                "Расход CONSUME за ["
                    + today.minusDays(90)
                    + ", "
                    + today
                    + "): "
                    + input.consumed(),
                "Физический остаток на "
                    + today
                    + ": "
                    + input.physical()
                    + "; доступный: "
                    + input.available(),
                "Поставки из запроса в горизонте [" + today + ", " + end + "): " + incoming,
                "Срок поставки: "
                    + command.leadTimeDays()
                    + "; страховые дни: "
                    + command.safetyDays()),
            formulas,
            assumptions,
            today);
    return new ForecastResult(
        command.sku(),
        input.name(),
        input.unit(),
        command.location(),
        today,
        end,
        days,
        today.minusDays(90),
        today.minusDays(1),
        input.consumed(),
        units(input.consumed()),
        input.physical(),
        input.available(),
        units(demandNumerator),
        units(safetyNumerator),
        units(reorderNumerator),
        incoming,
        units(needNumerator),
        quantity,
        input.price(),
        input.price() == null
            ? null
            : quantity.multiply(input.price()).setScale(2, RoundingMode.HALF_UP),
        "RUB",
        simulation.shortage(),
        order,
        arrival,
        units(simulation.expired()),
        units(simulation.unmet()),
        explanation,
        warnings);
  }

  private static BigDecimal units(BigDecimal numerator) {
    return numerator.divide(WINDOW, 6, RoundingMode.HALF_UP);
  }

  private record Simulation(
      LocalDate shortage, LocalDate reorderDate, BigDecimal expired, BigDecimal unmet) {}

  private static final class Lot {
    private final LocalDate expiry;
    private BigDecimal amount;

    private Lot(LocalDate expiry, BigDecimal amount) {
      this.expiry = expiry;
      this.amount = amount;
    }
  }

  private static Simulation simulate(
      ForecastCommand command, Input input, LocalDate end, BigDecimal reorder) {
    List<Lot> lots = new ArrayList<>();
    input.batches().stream()
        .filter(b -> !b.expiresOn().isBefore(input.today()) && b.quantity().signum() > 0)
        .sorted(
            Comparator.comparing(Batch::expiresOn)
                .thenComparing(Batch::receivedOn)
                .thenComparing(Batch::id))
        .forEach(b -> lots.add(new Lot(b.expiresOn(), b.quantity().multiply(WINDOW))));
    LocalDate shortage = null, reorderDate = null;
    BigDecimal expired = BigDecimal.ZERO, unmet = BigDecimal.ZERO;
    for (LocalDate date = input.today(); date.isBefore(end); date = date.plusDays(1)) {
      for (Lot lot : lots)
        if (lot.expiry.isBefore(date)) {
          expired = expired.add(lot.amount);
          lot.amount = BigDecimal.ZERO;
        }
      for (var delivery : command.openDeliveries())
        if (delivery.expectedDate().equals(date))
          lots.add(new Lot(LocalDate.MAX, delivery.quantity().multiply(WINDOW)));
      BigDecimal available =
          lots.stream().map(l -> l.amount).reduce(BigDecimal.ZERO, BigDecimal::add);
      if (input.consumed().signum() > 0 && reorderDate == null && available.compareTo(reorder) <= 0)
        reorderDate = date;
      BigDecimal remaining = input.consumed();
      for (Lot lot : lots) {
        BigDecimal used = remaining.min(lot.amount);
        lot.amount = lot.amount.subtract(used);
        remaining = remaining.subtract(used);
        if (remaining.signum() == 0) break;
      }
      if (remaining.signum() > 0) {
        if (shortage == null) shortage = date;
        unmet = unmet.add(remaining);
      }
    }
    return new Simulation(shortage, reorderDate, expired, unmet);
  }
}
