package com.nabeelsayyed.benefitsportal.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Checks the SES call with a fake SES client, so no AWS account is needed to run the tests. */
class SesEmailSenderTest {

    @Test
    void sendsBothHtmlAndPlainTextFromTheConfiguredAddress() {
        SesV2Client ses = mock(SesV2Client.class);
        SesEmailSender sender = new SesEmailSender(ses, "portal@example.com");

        sender.send("jesse@example.com", "Your claim", "<p>Hello</p>", "Hello");

        ArgumentCaptor<SendEmailRequest> request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        SendEmailRequest sent = request.getValue();
        assertThat(sent.fromEmailAddress()).isEqualTo("portal@example.com");
        assertThat(sent.destination().toAddresses()).containsExactly("jesse@example.com");
        assertThat(sent.content().simple().subject().data()).isEqualTo("Your claim");
        assertThat(sent.content().simple().body().html().data()).isEqualTo("<p>Hello</p>");
        assertThat(sent.content().simple().body().text().data()).isEqualTo("Hello");
    }
}
