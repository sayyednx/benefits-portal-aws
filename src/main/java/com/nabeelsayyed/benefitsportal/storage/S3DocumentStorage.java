package com.nabeelsayyed.benefitsportal.storage;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Keeps documents in an Amazon S3 bucket. The bucket itself is private, encrypted and
 * versioned (see terraform/main.tf). The app gets its AWS permissions from the EC2
 * instance's IAM role, so there are no AWS keys in the code or in environment variables.
 */
public class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3;
    private final String bucket;

    public S3DocumentStorage(S3Client s3, String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .serverSideEncryption(ServerSideEncryption.AES256)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] get(String key) {
        return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }
}
