package com.example.spa.stock.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends Repository<Batch, Long> {
  @Query(
      value =
          """
      select coalesce(sum(a.quantity_delta), 0)
      from movement_allocation a where a.position_id = :positionId
      """,
      nativeQuery = true)
  BigDecimal physicalQuantity(@Param("positionId") Long positionId);

  // Current ledger balance, filtered by expiry on the supplied business date.
  // This is not a historical/as-of ledger reconstruction.
  @Query(
      value =
          """
      select coalesce(sum(a.quantity_delta), 0)
      from movement_allocation a join batch b on b.id = a.batch_id
      where a.position_id = :positionId and b.expires_on >= :businessDate
      """,
      nativeQuery = true)
  BigDecimal availableQuantity(
      @Param("positionId") Long positionId, @Param("businessDate") LocalDate businessDate);

  @Query(
      value =
          """
      select b.id as batchId, b.batch_number as batchNumber,
             b.received_on as receivedOn, b.expires_on as expiresOn,
             b.unit_price as unitPrice, b.invoice_number as invoiceNumber,
             coalesce(sum(a.quantity_delta), 0) as quantity
      from batch b left join movement_allocation a on a.batch_id = b.id
      where b.position_id = :positionId
      group by b.id
      order by b.expires_on, b.received_on, b.id
      """,
      nativeQuery = true)
  List<BatchBalance> batchBalances(@Param("positionId") Long positionId);
}
