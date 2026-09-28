package com.example.spa.movement;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FefoAllocatorTest {
  private final LocalDate date = LocalDate.of(2026, 9, 28);

  @Test
  void sortsByExpiryThenReceiptThenIdAndSplitsExactly() {
    var batches =
        List.of(
            batch(4, 2, -1, "10"),
            batch(3, 0, -1, "2"),
            batch(2, 0, -2, "0.5"),
            batch(1, 0, -2, "1.25"));
    var result = FefoAllocator.allocate(batches, new BigDecimal("3"), date);
    assertThat(result)
        .containsExactly(
            new Allocation(1L, new BigDecimal("-1.25")),
            new Allocation(2L, new BigDecimal("-0.5")),
            new Allocation(3L, new BigDecimal("-1.25")));
    assertThat(batches.getFirst().id()).isEqualTo(4L);
  }

  @Test
  void excludesExpiredFutureAndEmptyBatchesAndReportsAvailable() {
    var batches =
        List.of(
            batch(1, -1, -2, "100"),
            batch(2, 2, 1, "100"),
            batch(3, 0, 0, "0"),
            batch(4, 0, 0, "2.5"));
    assertThatThrownBy(() -> FefoAllocator.allocate(batches, new BigDecimal("3"), date))
        .isInstanceOfSatisfying(
            MovementException.class,
            e -> assertThat(e.getAvailableQuantity()).isEqualByComparingTo("2.5"));
    assertThat(FefoAllocator.allocate(batches, new BigDecimal("2.5"), date))
        .containsExactly(new Allocation(4L, new BigDecimal("-2.5")));
  }

  @Test
  void rejectsNonPositiveRequests() {
    assertThatThrownBy(() -> FefoAllocator.allocate(List.of(), BigDecimal.ZERO, date))
        .isInstanceOf(MovementException.class);
  }

  private FefoAllocator.BatchStock batch(long id, int expiry, int received, String quantity) {
    return new FefoAllocator.BatchStock(
        id, date.plusDays(received), date.plusDays(expiry), new BigDecimal(quantity));
  }
}
