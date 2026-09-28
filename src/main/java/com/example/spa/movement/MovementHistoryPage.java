package com.example.spa.movement;

import java.util.List;

public record MovementHistoryPage(
    List<MovementHistoryEntry> items, long total, int limit, int offset) {
  public MovementHistoryPage {
    items = List.copyOf(items);
  }
}
