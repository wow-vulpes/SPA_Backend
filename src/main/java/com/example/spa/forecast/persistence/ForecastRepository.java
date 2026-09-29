package com.example.spa.forecast.persistence;

import com.example.spa.movement.persistence.Movement;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ForecastRepository extends Repository<Movement, Long> {
  @Query(
      value =
          """
      select b.unit_price from movement m
      join movement_allocation a on a.movement_id=m.id
      join batch b on b.id=a.batch_id
      where m.position_id=:position and m.operation_type='RECEIPT'
      order by m.operation_date desc, m.id desc, a.id desc limit 1
      """,
      nativeQuery = true)
  Optional<BigDecimal> latestReceiptPrice(@Param("position") Long position);
}
