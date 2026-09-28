package com.example.spa.catalog.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {
  @Id
  @Column(length = 64)
  private String code;

  @Column(nullable = false, length = 255)
  private String name;

  public Location(String code, String name) {
    this.code = code;
    this.name = name;
  }
}
