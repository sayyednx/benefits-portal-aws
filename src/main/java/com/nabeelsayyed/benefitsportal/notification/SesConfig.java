package com.nabeelsayyed.benefitsportal.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Switched on by notifications.sender=ses (set by Terraform on the EC2 instance). */
@Configuration
@ConditionalOnProperty(name = "notifications.sender", havingValue = "ses")
public class SesConfig {

    @Bean
    public SesV2Client sesClient(@Value("${notifications.ses-region}") String region) {
        return SesV2Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public EmailSender sesEmailSender(SesV2Client sesClient, @Value("${notifications.from}") String from) {
        return new SesEmailSender(sesClient, from);
    }
}
