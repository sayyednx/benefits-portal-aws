package com.nabeelsayyed.benefitsportal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * One shared clock for the whole app. Services ask this bean for "now" instead of calling
 * LocalDateTime.now() directly, so tests can swap in a fixed clock and check date rules
 * (appeal deadlines, which weeks can be certified) without waiting for real days to pass.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
