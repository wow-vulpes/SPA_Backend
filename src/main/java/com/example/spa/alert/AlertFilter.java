package com.example.spa.alert;

import com.example.spa.movement.MovementException;

public record AlertFilter(String sku, String location, int limit, int offset) {
  public AlertFilter {
    sku = identifier(sku);
    location = identifier(location);
    if (limit < 1 || limit > 100 || offset < 0) throw invalid();
  }

  public static AlertFilter parse(String sku, String location, String limit, String offset) {
    return new AlertFilter(sku, location, integer(limit, 50), integer(offset, 0));
  }

  private static String identifier(String value) {
    if (value == null) return null;
    String result = value.strip();
    if (result.isEmpty() || result.length() > 64) throw invalid();
    return result;
  }

  private static int integer(String value, int fallback) {
    if (value == null) return fallback;
    try {
      if (!value.matches("[0-9]+")) throw invalid();
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw invalid();
    }
  }

  private static MovementException invalid() {
    return MovementException.invalid(
        "INVALID_ALERT_FILTER", "SKU/объект: 1–64 символа; limit: 1–100; offset: 0–2147483647");
  }
}
