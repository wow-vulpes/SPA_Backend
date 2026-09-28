package com.example.spa.stock.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InventoryPositionRepository extends Repository<InventoryPosition, Long> {
  InventoryPosition save(InventoryPosition position);

  Optional<InventoryPosition> findById(Long id);

  Optional<InventoryPosition> findBySkuAndLocationCode(String sku, String locationCode);

  // Caller must open a transaction and hold the lock through allocation and commit.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InventoryPosition p where p.id = :id")
  Optional<InventoryPosition> findByIdForUpdate(@Param("id") Long id);
}
