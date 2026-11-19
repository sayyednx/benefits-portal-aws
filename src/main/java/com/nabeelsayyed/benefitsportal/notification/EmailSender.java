package com.nabeelsayyed.benefitsportal.notification;

/**
 * Sends one email. Like DocumentStorage, there are two versions, picked by a setting:
 * SMTP (used with the Mailpit test inbox in docker compose) and Amazon SES (used in AWS).
 */
public interface EmailSender {

    void send(String to, String subject, String html, String text);
}
