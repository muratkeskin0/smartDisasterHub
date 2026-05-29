package com.caglamurat.smartDisasterHub.service.mail;

public interface IEmailService {
    void sendHtmlEmail(String to, String subject, String htmlContent);
}
