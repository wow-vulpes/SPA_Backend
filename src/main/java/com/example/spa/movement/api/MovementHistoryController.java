package com.example.spa.movement.api;

import com.example.spa.movement.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
public class MovementHistoryController {
  private final MovementHistoryService service;
  private final MovementHistoryMapper mapper;

  @GetMapping
  public MovementHistoryResponse history(
      @RequestParam(required = false) String sku,
      @RequestParam(required = false) String location,
      @RequestParam(required = false) String type,
      @RequestParam(name = "date_from", required = false) String from,
      @RequestParam(name = "date_to", required = false) String to,
      @RequestParam(required = false) String limit,
      @RequestParam(required = false) String offset) {
    return mapper.toResponse(
        service.find(MovementHistoryFilter.parse(sku, location, type, from, to, limit, offset)));
  }
}
