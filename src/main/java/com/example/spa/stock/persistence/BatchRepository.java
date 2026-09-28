package com.example.spa.stock.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface BatchRepository extends Repository<Batch, Long> {
  Batch save(Batch batch);

  Optional<Batch> findById(Long id);
}
