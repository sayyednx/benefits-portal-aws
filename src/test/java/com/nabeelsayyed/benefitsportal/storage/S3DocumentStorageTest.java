package com.nabeelsayyed.benefitsportal.storage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Checks the S3 calls with a fake S3 client, so no AWS account is needed to run the tests. */
class S3DocumentStorageTest {

    @Test
    void putsTheFileInTheRightBucketEncrypted() {
        S3Client s3 = mock(S3Client.class);
        S3DocumentStorage storage = new S3DocumentStorage(s3, "benefits-portal-documents-123");

        storage.put("claims/7/abc.pdf", new byte[]{1, 2, 3}, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("benefits-portal-documents-123");
        assertThat(request.getValue().key()).isEqualTo("claims/7/abc.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
        assertThat(request.getValue().serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
    }
}
