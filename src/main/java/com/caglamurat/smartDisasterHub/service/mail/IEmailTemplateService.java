package com.caglamurat.smartDisasterHub.service.mail;

public interface IEmailTemplateService {
    String buildActivationEmail(String fullName, String activationLink);

    String buildEmailChangeEmail(String fullName, String currentEmail, String newEmail, String confirmationLink);

    String buildPasswordResetEmail(String fullName, String resetLink);
}
