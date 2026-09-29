package com.example.spa.validation;

import com.example.spa.movement.MovementException;
import java.time.LocalDate;

/** Values accepted by the public API and safely representable in the database. */
public final class InputValues {
  private InputValues() {}

  public static void databaseText(String value) {
    if (value != null && value.indexOf('\u0000') >= 0) {
      throw MovementException.invalid("INVALID_FIELD", "Строка не может содержать нулевой символ");
    }
  }

  public static void calendarDate(LocalDate value) {
    if (value != null && (value.getYear() < 1 || value.getYear() > 9999)) {
      throw MovementException.invalid(
          "INVALID_DATE", "Дата должна быть в диапазоне 0001-01-01…9999-12-31");
    }
  }
}
