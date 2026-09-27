package com.example.spa.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TimeProperties.class)
public class TimeConfiguration {
  @Bean
  Clock applicationClock(TimeProperties properties) {
    return Clock.system(properties.timeZone());
  }
}
