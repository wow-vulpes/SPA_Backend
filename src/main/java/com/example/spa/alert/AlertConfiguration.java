package com.example.spa.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AlertProperties.class)
public class AlertConfiguration {}
