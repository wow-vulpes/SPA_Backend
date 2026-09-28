package com.example.spa.movement.api;

import com.example.spa.movement.MovementCommand;
import com.example.spa.movement.MovementException;
import com.example.spa.movement.MovementResult;
import com.example.spa.movement.MovementType;
import java.util.Locale;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MovementMapper {
  MovementCommand toCommand(CreateMovementRequest request);

  CreateMovementResponse toResponse(MovementResult result);

  default MovementType toType(String value) {
    if (value == null) return null;
    try {
      return MovementType.valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      throw MovementException.invalid("INVALID_TYPE", "Неизвестный тип операции");
    }
  }
}
