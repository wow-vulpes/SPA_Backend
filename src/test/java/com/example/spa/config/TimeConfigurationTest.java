package com.example.spa.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class TimeConfigurationTest {
  @Test
  void createsClockInConfiguredZone() {
    Clock clock = new TimeConfiguration().applicationClock(new TimeProperties(ZoneId.of("UTC")));
    assertThat(clock.getZone()).isEqualTo(ZoneId.of("UTC"));
  }

  @Test
  void supportsBusinessZone() {
    Clock clock =
        new TimeConfiguration().applicationClock(new TimeProperties(ZoneId.of("Europe/Moscow")));
    assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Moscow"));
  }
}
