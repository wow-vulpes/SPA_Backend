package com.example.spa.movement.persistence;

import com.example.spa.movement.MovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long positionId;

  @Column(nullable = false, updatable = false)
  private LocalDate operationDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private MovementType operationType;

  @Column(nullable = false, precision = 19, scale = 6, updatable = false)
  private BigDecimal quantity;

  @Column(nullable = false, length = 128, updatable = false)
  private String documentNumber;

  public Movement(
      Long positionId,
      LocalDate operationDate,
      MovementType operationType,
      BigDecimal quantity,
      String documentNumber) {
    this.positionId = positionId;
    this.operationDate = operationDate;
    this.operationType = operationType;
    this.quantity = quantity;
    this.documentNumber = documentNumber;
  }
}
