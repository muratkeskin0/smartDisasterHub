package com.caglamurat.smartDisasterHub.service.mail;

public interface IEmailService {
    boolean isConfigured();

    void sendHtmlEmail(String to, String subject, String htmlContent);
}
