package com.nabeelsayyed.benefitsportal.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** Switched on by documents.storage=s3 (set by Terraform on the EC2 instance). */
@Configuration
@ConditionalOnProperty(name = "documents.storage", havingValue = "s3")
public class S3StorageConfig {

    @Bean
    public S3Client s3Client(@Value("${documents.s3.region}") String region) {
        // No keys here: the SDK finds credentials itself (on EC2, from the instance's IAM role).
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public DocumentStorage s3DocumentStorage(S3Client s3Client, @Value("${documents.s3.bucket}") String bucket) {
        return new S3DocumentStorage(s3Client, bucket);
    }
}
