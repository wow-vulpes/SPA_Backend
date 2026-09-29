package com.example.spa.forecast.api;

import com.example.spa.forecast.ForecastService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/forecast")
@RequiredArgsConstructor
public class ForecastController {
  private final ForecastService service;
  private final ForecastMapper mapper;

  @PostMapping
  public ForecastResponse calculate(@Valid @RequestBody ForecastRequest request) {
    return mapper.toResponse(service.calculate(mapper.toCommand(request)));
  }
}
