package com.example.spa.alert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AlertConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner().withUserConfiguration(AlertConfiguration.class);

  @Test
  void defaultsAndOverridesAreBound() {
    context.run(
        c ->
            assertThat(c.getBean(AlertProperties.class))
                .isEqualTo(new AlertProperties(30, 30, 90)));
    context
        .withPropertyValues(
            "app.alerts.shortage-days=7",
            "app.alerts.expiry-days=0",
            "app.alerts.inactivity-days=14")
        .run(
            c ->
                assertThat(c.getBean(AlertProperties.class))
                    .isEqualTo(new AlertProperties(7, 0, 14)));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "shortage-days=0",
        "expiry-days=-1",
        "inactivity-days=0",
        "shortage-days=3651",
        "expiry-days=3651",
        "inactivity-days=3651",
        "expiry-days=1.5"
      })
  void invalidThresholdStopsStartup(String property) {
    context.withPropertyValues("app.alerts." + property).run(c -> assertThat(c).hasFailed());
  }
}
