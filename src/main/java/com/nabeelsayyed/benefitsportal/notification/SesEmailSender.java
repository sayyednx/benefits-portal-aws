package com.nabeelsayyed.benefitsportal.notification;

import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.*;

/**
 * Sends email through Amazon SES (Simple Email Service). Like the S3 storage, it gets its
 * permission from the EC2 instance's IAM role, so there are no SMTP passwords or AWS keys.
 */
public class SesEmailSender implements EmailSender {

    private final SesV2Client ses;
    private final String from;

    public SesEmailSender(SesV2Client ses, String from) {
        this.ses = ses;
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html, String text) {
        ses.sendEmail(SendEmailRequest.builder()
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .html(Content.builder().data(html).charset("UTF-8").build())
                                        .text(Content.builder().data(text).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build())
                .build());
    }
}
