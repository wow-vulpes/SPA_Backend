package com.example.spa.movement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FefoAllocator {
  private FefoAllocator() {}

  public record BatchStock(
      Long id, LocalDate receivedOn, LocalDate expiresOn, BigDecimal quantity) {}

  public static List<Allocation> allocate(
      List<BatchStock> batches, BigDecimal requested, LocalDate date) {
    if (requested.signum() <= 0) {
      throw MovementException.invalid(
          "INVALID_QUANTITY", "Количество расхода должно быть положительным");
    }
    List<BatchStock> eligible =
        batches.stream()
            .filter(
                b ->
                    !b.receivedOn().isAfter(date)
                        && !b.expiresOn().isBefore(date)
                        && b.quantity().signum() > 0)
            .sorted(
                Comparator.comparing(BatchStock::expiresOn)
                    .thenComparing(BatchStock::receivedOn)
                    .thenComparing(BatchStock::id))
            .toList();
    BigDecimal available =
        eligible.stream().map(BatchStock::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (requested.compareTo(available) > 0) {
      throw MovementException.insufficient(available);
    }
    BigDecimal remaining = requested;
    List<Allocation> result = new ArrayList<>();
    for (BatchStock batch : eligible) {
      if (remaining.signum() == 0) break;
      BigDecimal amount = remaining.min(batch.quantity());
      result.add(new Allocation(batch.id(), amount.negate()));
      remaining = remaining.subtract(amount);
    }
    return List.copyOf(result);
  }
}
