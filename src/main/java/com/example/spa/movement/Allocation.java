package com.example.spa.movement;

import java.math.BigDecimal;

public record Allocation(Long batchId, BigDecimal quantityDelta) {}
