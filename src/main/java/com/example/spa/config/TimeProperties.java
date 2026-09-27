package com.example.spa.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record TimeProperties(ZoneId timeZone) {}
