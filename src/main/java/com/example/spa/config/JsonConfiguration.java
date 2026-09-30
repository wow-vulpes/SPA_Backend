package com.example.spa.config;

import java.time.LocalDate;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JsonConfiguration {
  @Bean
  JsonMapperBuilderCustomizer strictRequestJson() {
    return builder ->
        builder
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .addModule(
                new SimpleModule("strict-request-dates")
                    .addDeserializer(LocalDate.class, new StrictLocalDateDeserializer()));
  }
}
