package com.example.spa.movement;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovementHistoryFilterTest {
  @Test
  void defaultsAndNormalization() {
    var f =
        MovementHistoryFilter.parse(
            " OIL ", " SPA ", " CoNsUmE ", "2026-01-01", "2026-01-01", null, null);
    assertThat(f.sku()).isEqualTo("OIL");
    assertThat(f.location()).isEqualTo("SPA");
    assertThat(f.type()).isEqualTo(MovementType.CONSUME);
    assertThat(f.limit()).isEqualTo(50);
    assertThat(f.offset()).isZero();
    assertThat(f.dateFrom()).isEqualTo(f.dateTo());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "-1", "0", "101", "1.5", "abc", "2147483648"})
  void rejectsInvalidLimits(String value) {
    assertThatThrownBy(() -> MovementHistoryFilter.parse(null, null, null, null, null, value, null))
        .isInstanceOf(MovementException.class);
  }

  @Test
  void acceptsPaginationBoundaries() {
    var f = MovementHistoryFilter.parse(null, null, null, null, null, "100", "2147483647");
    assertThat(f.limit()).isEqualTo(100);
    assertThat(f.offset()).isEqualTo(Integer.MAX_VALUE);
  }
}
