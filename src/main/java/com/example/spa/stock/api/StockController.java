package com.example.spa.stock.api;

import com.example.spa.stock.StockFilter;
import com.example.spa.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {
  private final StockService service;
  private final StockMapper mapper;

  @GetMapping
  public StockResponse.Page list(
      @RequestParam(required = false) String sku,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String limit,
      @RequestParam(required = false) String offset) {
    return mapper.toResponse(service.list(StockFilter.parse(sku, location, limit, offset)));
  }

  @GetMapping("/{sku}")
  public StockResponse.Detail detail(@PathVariable String sku) {
    return mapper.toResponse(service.detail(sku));
  }
}
