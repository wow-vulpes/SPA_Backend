package com.example.spa.stock.persistence;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface StockReadRepository extends Repository<InventoryPosition, Long> {
  String FILTER =
      """
      from inventory_position p
      where (cast(:sku as varchar) is null or p.sku = :sku)
        and (cast(:location as varchar) is null or p.location_code = :location)
      """;

  @Query(value = "select count(*) " + FILTER, nativeQuery = true)
  long countPositions(@Param("sku") String sku, @Param("location") String location);

  @Query(
      value =
          """
      with selected as (
        select p.*
      """
              + FILTER
              + """
        order by p.sku, p.location_code limit :limit offset :offset
      ), balances as (
        select b.position_id, b.id, b.expires_on, coalesce(sum(a.quantity_delta),0) as quantity
        from selected s join batch b on b.position_id=s.id
        left join movement_allocation a on a.batch_id=b.id
        group by b.id
      ), totals as (
        select position_id, sum(quantity) as physical,
          coalesce(sum(quantity) filter(where expires_on >= :today),0) as available,
          min(expires_on) filter(where quantity > 0) as nearest
        from balances group by position_id
      ), consumption as (
        select m.position_id, sum(m.quantity) as quantity
        from movement m join selected s on s.id=m.position_id
        where m.operation_type='CONSUME' and m.operation_date >= :fromDate and m.operation_date < :today
        group by m.position_id
      )
      select s.id as positionId, s.sku as sku, product.name as name, product.unit as unit,
        s.location_code as location, l.name as locationName,
        coalesce(t.physical,0) as currentStock, coalesce(t.available,0) as availableStock,
        coalesce(c.quantity,0) as consumed, t.nearest as nearestExpiry
      from selected s join product on product.sku=s.sku join location l on l.code=s.location_code
      left join totals t on t.position_id=s.id left join consumption c on c.position_id=s.id
      order by s.sku,s.location_code
      """,
      nativeQuery = true)
  List<StockSummary> summaries(
      @Param("sku") String sku,
      @Param("location") String location,
      @Param("today") LocalDate today,
      @Param("fromDate") LocalDate from,
      @Param("limit") int limit,
      @Param("offset") int offset);

  @Query(
      value =
          """
      select b.position_id as positionId, b.id as batchId, b.batch_number as batchNumber,
        b.received_on as receivedOn, b.expires_on as expiresOn, b.unit_price as unitPrice,
        b.invoice_number as invoiceNumber, coalesce(sum(a.quantity_delta),0) as quantity
      from inventory_position p join batch b on b.position_id=p.id
      left join movement_allocation a on a.batch_id=b.id
      where p.sku=:sku
      group by b.id
      order by b.position_id,b.expires_on,b.received_on,b.id
      """,
      nativeQuery = true)
  List<PositionBatchBalance> batches(@Param("sku") String sku);
}
