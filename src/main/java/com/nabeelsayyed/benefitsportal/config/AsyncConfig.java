package com.nabeelsayyed.benefitsportal.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Lets some work run in the background (methods marked @Async). Emails use this, so a
 * claimant never waits on, or gets an error from, a slow or broken mail server.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
