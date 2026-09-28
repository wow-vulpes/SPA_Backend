package com.example.spa.movement.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MovementRepository extends Repository<Movement, Long> {
  Movement save(Movement movement);

  Optional<Movement> findById(Long id);

  Optional<Movement> findByDocumentNumber(String documentNumber);

  Optional<Movement> findFirstByPositionIdOrderByOperationDateDescIdDesc(Long positionId);
}
