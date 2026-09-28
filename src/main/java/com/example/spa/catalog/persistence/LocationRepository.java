package com.example.spa.catalog.persistence;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface LocationRepository extends Repository<Location, String> {
  Location save(Location location);

  Optional<Location> findById(String code);
}
