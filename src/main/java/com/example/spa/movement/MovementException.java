package com.example.spa.movement;

import java.math.BigDecimal;

public class MovementException extends RuntimeException {
  public enum Kind {
    INVALID,
    NOT_FOUND,
    CONFLICT
  }

  private final Kind kind;
  private final String code;
  private final BigDecimal availableQuantity;

  private MovementException(Kind kind, String code, String message, BigDecimal availableQuantity) {
    super(message);
    this.kind = kind;
    this.code = code;
    this.availableQuantity = availableQuantity;
  }

  public static MovementException invalid(String code, String message) {
    return new MovementException(Kind.INVALID, code, message, null);
  }

  public static MovementException notFound(String code, String message) {
    return new MovementException(Kind.NOT_FOUND, code, message, null);
  }

  public static MovementException duplicateDocument() {
    return new MovementException(
        Kind.CONFLICT, "DUPLICATE_DOCUMENT", "Документ уже загружен", null);
  }

  public static MovementException insufficient(BigDecimal available) {
    return new MovementException(
        Kind.INVALID, "INSUFFICIENT_STOCK", "Недостаточно доступного остатка", available);
  }

  public Kind getKind() {
    return kind;
  }

  public String getCode() {
    return code;
  }

  public BigDecimal getAvailableQuantity() {
    return availableQuantity;
  }
}
