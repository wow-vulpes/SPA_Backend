package com.example.spa.movement.api;

import com.example.spa.movement.*;
import java.util.Locale;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MovementHistoryMapper {
  MovementHistoryResponse toResponse(MovementHistoryPage page);

  default String toType(MovementType type) {
    return type.name().toLowerCase(Locale.ROOT);
  }
}
