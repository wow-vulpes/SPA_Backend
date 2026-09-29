package com.example.spa.alert.api;

import com.example.spa.alert.AlertView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface AlertMapper {
  AlertResponse.Page toResponse(AlertView.Page page);
}
