package com.example.spa.movement.persistence;

import java.util.List;
import org.springframework.data.repository.Repository;

public interface MovementAllocationRepository extends Repository<MovementAllocation, Long> {
  MovementAllocation save(MovementAllocation allocation);

  List<MovementAllocation> findAllByMovementIdOrderById(Long movementId);
}
