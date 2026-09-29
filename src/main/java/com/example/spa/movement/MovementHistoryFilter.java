package com.example.spa.movement;

import com.example.spa.validation.InputValues;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public record MovementHistoryFilter(
    String sku,
    String location,
    MovementType type,
    LocalDate dateFrom,
    LocalDate dateTo,
    int limit,
    int offset) {
  public MovementHistoryFilter {
    sku = identifier(sku);
    location = identifier(location);
    if (limit < 1 || limit > 100 || offset < 0)
      throw invalid("limit: 1–100, offset: неотрицательное целое число");
    if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo))
      throw invalid("date_from не может быть позже date_to");
  }

  public static MovementHistoryFilter parse(
      String sku,
      String location,
      String type,
      String from,
      String to,
      String limit,
      String offset) {
    return new MovementHistoryFilter(
        sku,
        location,
        parseType(type),
        date(from),
        date(to),
        integer(limit, 50),
        integer(offset, 0));
  }

  private static String identifier(String value) {
    InputValues.databaseText(value);
    if (value == null) return null;
    String normalized = value.strip();
    if (normalized.isEmpty() || normalized.length() > 64)
      throw invalid("Фильтр SKU/объекта должен содержать от 1 до 64 символов");
    return normalized;
  }

  private static MovementType parseType(String value) {
    if (value == null) return null;
    try {
      return MovementType.valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw invalid("Неизвестный тип движения");
    }
  }

  private static LocalDate date(String value) {
    if (value == null) return null;
    try {
      if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
        throw invalid("Дата должна иметь формат YYYY-MM-DD");
      LocalDate result = LocalDate.parse(value);
      InputValues.calendarDate(result);
      return result;
    } catch (DateTimeParseException e) {
      throw invalid("Некорректная календарная дата");
    }
  }

  private static int integer(String value, int fallback) {
    if (value == null) return fallback;
    try {
      if (!value.matches("[0-9]+"))
        throw invalid("limit и offset должны быть целыми неотрицательными числами");
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw invalid("Слишком большое значение limit/offset");
    }
  }

  private static MovementException invalid(String message) {
    return MovementException.invalid("INVALID_HISTORY_FILTER", message);
  }
}
