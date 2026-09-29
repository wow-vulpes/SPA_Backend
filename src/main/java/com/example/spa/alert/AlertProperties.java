package com.example.spa.alert;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.alerts")
public record AlertProperties(
    @DefaultValue("30") @Min(1) @Max(3650) int shortageDays,
    @DefaultValue("30") @Min(0) @Max(3650) int expiryDays,
    @DefaultValue("90") @Min(1) @Max(3650) int inactivityDays) {}
