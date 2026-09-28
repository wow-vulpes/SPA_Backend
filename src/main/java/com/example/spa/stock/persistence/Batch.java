package com.example.spa.stock.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "batch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Batch {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long positionId;

  @Column(nullable = false, length = 128, updatable = false)
  private String batchNumber;

  @Column(nullable = false, updatable = false)
  private LocalDate receivedOn;

  @Column(nullable = false, updatable = false)
  private LocalDate expiresOn;

  @Column(nullable = false, precision = 19, scale = 4, updatable = false)
  private BigDecimal unitPrice;

  @Column(nullable = false, length = 128, updatable = false)
  private String invoiceNumber;

  public Batch(
      Long positionId,
      String batchNumber,
      LocalDate receivedOn,
      LocalDate expiresOn,
      BigDecimal unitPrice,
      String invoiceNumber) {
    this.positionId = positionId;
    this.batchNumber = batchNumber;
    this.receivedOn = receivedOn;
    this.expiresOn = expiresOn;
    this.unitPrice = unitPrice;
    this.invoiceNumber = invoiceNumber;
  }
}
