package com.example.spa.movement;

import com.example.spa.movement.persistence.MovementHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class MovementHistoryService {
  private final MovementHistoryRepository repository;

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 15)
  public MovementHistoryPage find(MovementHistoryFilter filter) {
    return repository.find(filter);
  }
}
