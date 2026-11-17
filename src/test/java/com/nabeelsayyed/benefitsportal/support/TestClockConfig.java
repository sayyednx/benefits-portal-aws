package com.nabeelsayyed.benefitsportal.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.time.ZoneId;

/** Import this in a test to replace the real clock with one the test controls. */
@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(LocalDateTime.of(2026, 3, 2, 9, 0), ZoneId.of("America/New_York"));
    }
}
