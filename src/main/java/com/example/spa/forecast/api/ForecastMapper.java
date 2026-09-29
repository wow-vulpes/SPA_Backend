package com.example.spa.forecast.api;

import com.example.spa.forecast.ForecastCommand;
import com.example.spa.forecast.ForecastResult;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ForecastMapper {
  ForecastCommand toCommand(ForecastRequest request);

  ForecastResponse toResponse(ForecastResult result);
}
