package com.example.spa.movement.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movement_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MovementAllocation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long movementId;

  @Column(nullable = false, updatable = false)
  private Long batchId;

  @Column(nullable = false, updatable = false)
  private Long positionId;

  @Column(nullable = false, precision = 19, scale = 6, updatable = false)
  private BigDecimal quantityDelta;

  public MovementAllocation(
      Long movementId, Long batchId, Long positionId, BigDecimal quantityDelta) {
    this.movementId = movementId;
    this.batchId = batchId;
    this.positionId = positionId;
    this.quantityDelta = quantityDelta;
  }
}
