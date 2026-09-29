package com.example.spa.alert.api;

import com.example.spa.alert.AlertFilter;
import com.example.spa.alert.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
  private final AlertService service;
  private final AlertMapper mapper;

  @GetMapping
  public AlertResponse.Page list(
      @RequestParam(required = false) String sku,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String limit,
      @RequestParam(required = false) String offset,
      @RequestParam(name = "lead_time_days", required = false) String leadTimeDays) {
    return mapper.toResponse(
        service.list(AlertFilter.parse(sku, location, limit, offset, leadTimeDays)));
  }
}
