package com.example.spa.config;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** Reject timestamps and numeric date coercions before an inventory operation is accepted. */
public final class StrictLocalDateDeserializer extends ValueDeserializer<LocalDate> {
  @Override
  public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
    if (!parser.hasToken(JsonToken.VALUE_STRING)) {
      return (LocalDate) context.handleUnexpectedToken(LocalDate.class, parser);
    }
    String value = parser.getString();
    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
      throw context.weirdStringException(
          value, LocalDate.class, "Expected a date in YYYY-MM-DD format");
    }
    try {
      LocalDate date = LocalDate.parse(value);
      if (date.getYear() < 1) {
        throw context.weirdStringException(
            value, LocalDate.class, "Year must be between 0001 and 9999");
      }
      return date;
    } catch (DateTimeParseException error) {
      throw context.weirdStringException(value, LocalDate.class, "Invalid calendar date");
    }
  }
}
