package com.example.spa.catalog.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ProductRepository extends Repository<Product, String> {
  Product save(Product product);

  Optional<Product> findById(String sku);
}
