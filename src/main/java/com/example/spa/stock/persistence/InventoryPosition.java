package com.example.spa.stock.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory_position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryPosition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64, updatable = false)
  private String sku;

  @Column(nullable = false, length = 64, updatable = false)
  private String locationCode;

  @Column(nullable = false, updatable = false)
  private LocalDate registeredOn;

  public InventoryPosition(String sku, String locationCode, LocalDate registeredOn) {
    this.sku = sku;
    this.locationCode = locationCode;
    this.registeredOn = registeredOn;
  }
}
