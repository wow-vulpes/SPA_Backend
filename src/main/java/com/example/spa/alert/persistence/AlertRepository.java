package com.example.spa.alert.persistence;

import com.example.spa.stock.persistence.InventoryPosition;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AlertRepository extends Repository<InventoryPosition, Long> {
  // Aggregate allocations and movement headers independently to avoid multiplying consumption.
  String ALERTS =
      """
      with selected as (
        select p.id, p.sku, p.location_code, product.unit
        from inventory_position p join product on product.sku=p.sku
        where (cast(:sku as varchar) is null or p.sku=:sku)
          and (cast(:location as varchar) is null or p.location_code=:location)
      ), balances as (
        select b.id, b.position_id, b.batch_number, b.expires_on,
          coalesce(sum(a.quantity_delta),0) as quantity
        from selected s join batch b on b.position_id=s.id
        left join movement_allocation a on a.batch_id=b.id
        group by b.id
      ), totals as (
        select position_id, sum(quantity) as physical,
          coalesce(sum(quantity) filter(where expires_on >= :today),0) as available
        from balances group by position_id
      ), history as (
        select m.position_id, max(m.operation_date) as last_date,
          coalesce(sum(m.quantity) filter(where m.operation_type='CONSUME'
            and m.operation_date >= :fromDate and m.operation_date < :today),0) as consumed
        from movement m join selected s on s.id=m.position_id group by m.position_id
      ), metrics as (
        select s.*, coalesce(t.physical,0) as physical, coalesce(t.available,0) as available,
          coalesce(h.consumed,0) as consumed, h.last_date
        from selected s left join totals t on t.position_id=s.id
        left join history h on h.position_id=s.id
      ), alerts as (
        select m.*, 'SHORTAGE' as type,
          case when m.available=0 then 'CRITICAL' else 'WARNING' end as severity,
          cast(null as bigint) as batch_id, cast(null as varchar) as batch_number,
          cast(null as numeric) as batch_quantity, cast(null as date) as expires_on
        from metrics m where m.consumed>0 and m.available*90 < m.consumed*:leadTimeDays
        union all
        select m.*, 'NO_MOVEMENT', 'INFO', null, null, null, null
        from metrics m where m.physical>0 and m.last_date <= :inactiveThrough
        union all
        select m.*, case when b.expires_on < :today then 'EXPIRED' else 'EXPIRING' end,
          case when b.expires_on < :today then 'CRITICAL' else 'WARNING' end,
          b.id, b.batch_number, b.quantity, b.expires_on
        from metrics m join balances b on b.position_id=m.id
        where b.quantity>0 and b.expires_on <= :expiryThrough
      )
      """;

  @Query(value = ALERTS + "select count(*) from alerts", nativeQuery = true)
  long countAlerts(
      @Param("sku") String sku,
      @Param("location") String location,
      @Param("today") LocalDate today,
      @Param("fromDate") LocalDate from,
      @Param("leadTimeDays") int leadTimeDays,
      @Param("inactiveThrough") LocalDate inactiveThrough,
      @Param("expiryThrough") LocalDate expiryThrough);

  @Query(
      value =
          ALERTS
              + """
      select sku, location_code as location, unit, type, severity,
        physical as currentStock, available as availableStock, consumed,
        last_date as lastMovementDate, batch_id as batchId, batch_number as batchNumber,
        batch_quantity as batchQuantity, expires_on as expiresOn
      from alerts
      order by case severity when 'CRITICAL' then 0 when 'WARNING' then 1 else 2 end,
        sku, location_code, type, batch_id nulls first
      limit :limit offset :offset
      """,
      nativeQuery = true)
  List<AlertRow> alerts(
      @Param("sku") String sku,
      @Param("location") String location,
      @Param("today") LocalDate today,
      @Param("fromDate") LocalDate from,
      @Param("leadTimeDays") int leadTimeDays,
      @Param("inactiveThrough") LocalDate inactiveThrough,
      @Param("expiryThrough") LocalDate expiryThrough,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
