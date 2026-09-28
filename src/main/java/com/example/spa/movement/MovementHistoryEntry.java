package com.example.spa.movement;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovementHistoryEntry(
    Long id,
    String sku,
    String location,
    LocalDate operationDate,
    MovementType type,
    BigDecimal quantity,
    String documentNumber) {}
