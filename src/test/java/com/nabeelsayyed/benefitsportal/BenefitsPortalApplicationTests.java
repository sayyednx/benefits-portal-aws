package com.nabeelsayyed.benefitsportal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class BenefitsPortalApplicationTests {

    @Test
    void contextLoads() {
        // Fails fast if wiring (security config, JPA, Thymeleaf) is broken.
    }
}
