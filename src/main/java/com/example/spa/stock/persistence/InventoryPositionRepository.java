package com.example.spa.stock.persistence;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface InventoryPositionRepository extends Repository<InventoryPosition, Long> {
  InventoryPosition save(InventoryPosition position);

  Optional<InventoryPosition> findById(Long id);

  Optional<InventoryPosition> findBySkuAndLocationCode(String sku, String locationCode);

  @Modifying
  @Query(
      value =
          """
      insert into inventory_position(sku, location_code, registered_on)
      values (:sku, :location, :date)
      on conflict (sku, location_code) do nothing
      """,
      nativeQuery = true)
  int insertIfAbsent(
      @Param("sku") String sku, @Param("location") String location, @Param("date") LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InventoryPosition p where p.id = :id")
  Optional<InventoryPosition> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from InventoryPosition p where p.sku = :sku and p.locationCode = :location")
  Optional<InventoryPosition> findForUpdate(
      @Param("sku") String sku, @Param("location") String location);
}
