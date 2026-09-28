package com.example.spa.movement;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.spa.catalog.persistence.*;
import com.example.spa.movement.persistence.*;
import com.example.spa.stock.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.Test;

class MovementServiceTest {
  @Test
  void unknownProductDoesNotWriteAnything() {
    var products = mock(ProductRepository.class);
    var locations = mock(LocationRepository.class);
    var positions = mock(InventoryPositionRepository.class);
    var batches = mock(BatchRepository.class);
    var movements = mock(MovementRepository.class);
    var allocations = mock(MovementAllocationRepository.class);
    var stock = mock(StockRepository.class);
    var clock = Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneId.of("Europe/Moscow"));
    var service =
        new MovementService(
            products, locations, positions, batches, movements, allocations, stock, clock);
    var command =
        new MovementCommand(
            "UNKNOWN",
            "SPA",
            LocalDate.of(2026, 9, 28),
            MovementType.CONSUME,
            BigDecimal.ONE,
            "D",
            null,
            null);
    assertThatThrownBy(() -> service.post(command))
        .isInstanceOfSatisfying(
            MovementException.class, e -> assertThat(e.getCode()).isEqualTo("SKU_NOT_FOUND"));
    verify(products).findById("UNKNOWN");
    verifyNoInteractions(locations, positions, batches, movements, allocations, stock);
  }
}
