package com.example.spa.stock.api;

import com.example.spa.stock.StockView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface StockMapper {
  StockResponse.Page toResponse(StockView.Page page);

  StockResponse.Detail toResponse(StockView.Detail detail);
}
