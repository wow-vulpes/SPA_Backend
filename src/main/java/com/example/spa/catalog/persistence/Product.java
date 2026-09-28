package com.example.spa.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
  @Id
  @Column(length = 64)
  private String sku;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(nullable = false, length = 32)
  private String unit;

  public Product(String sku, String name, String unit) {
    this.sku = sku;
    this.name = name;
    this.unit = unit;
  }
}
