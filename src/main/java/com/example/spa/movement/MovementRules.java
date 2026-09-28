package com.example.spa.movement;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class MovementRules {
  private MovementRules() {}

  public static void validate(MovementCommand command, LocalDate today) {
    require(command != null, "INVALID_REQUEST", "Необходима операция");
    text(command.sku(), 64, "SKU");
    text(command.location(), 64, "Объект");
    text(command.documentNumber(), 128, "Номер документа");
    require(command.type() != null, "INVALID_TYPE", "Необходим тип операции");
    require(command.operationDate() != null, "INVALID_DATE", "Необходима дата операции");
    require(
        !command.operationDate().isAfter(today),
        "FUTURE_DATE",
        "Дата операции не может быть в будущем");
    decimal(command.quantity(), 13, 6);
    require(
        command.type() == MovementType.CORRECTION
            ? command.quantity().signum() != 0
            : command.quantity().signum() > 0,
        "INVALID_QUANTITY",
        "Количество должно быть положительным; корректировка допускает ненулевое знаковое значение");
    require(
        command.batchId() == null || command.batchId() > 0,
        "INVALID_BATCH",
        "batch_id должен быть положительным");
    switch (command.type()) {
      case CONSUME ->
          require(
              command.batchId() == null && command.batch() == null,
              "INVALID_BATCH",
              "При расходе партии выбираются автоматически по FEFO");
      case RECEIPT ->
          require(
              (command.batchId() == null) != (command.batch() == null),
              "INVALID_BATCH",
              "Для поступления задайте либо batch_id, либо batch");
      default ->
          require(
              command.batchId() != null && command.batch() == null,
              "INVALID_BATCH",
              "Для этой операции требуется batch_id без batch");
    }
    if (command.batch() != null) {
      text(command.batch().number(), 128, "Номер партии");
      text(command.batch().invoiceNumber(), 128, "Накладная");
      require(command.batch().expiresOn() != null, "INVALID_BATCH", "Необходим срок годности");
      decimal(command.batch().unitPrice(), 15, 4);
      require(
          command.batch().unitPrice().signum() >= 0,
          "INVALID_PRICE",
          "Цена не может быть отрицательной");
    }
  }

  private static void text(String value, int max, String name) {
    require(
        value != null && !value.isBlank() && value.length() <= max,
        "INVALID_FIELD",
        name + ": обязательная строка длиной до " + max);
  }

  private static void decimal(BigDecimal value, int integers, int fraction) {
    require(value != null, "INVALID_NUMBER", "Необходимо числовое значение");
    require(
        value.scale() <= fraction && value.precision() - value.scale() <= integers,
        "INVALID_PRECISION",
        "Превышена допустимая точность числового значения");
  }

  private static void require(boolean condition, String code, String message) {
    if (!condition) throw MovementException.invalid(code, message);
  }
}
