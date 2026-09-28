package com.example.spa.movement.api;

import com.example.spa.movement.MovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movements")
@RequiredArgsConstructor
public class MovementController {
  private final MovementService service;
  private final MovementMapper mapper;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateMovementResponse create(@Valid @RequestBody CreateMovementRequest request) {
    return mapper.toResponse(service.post(mapper.toCommand(request)));
  }
}
