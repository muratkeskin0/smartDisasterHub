package com.caglamurat.smartDisasterHub.service.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class EmailTemplateService implements IEmailTemplateService {

    private static final String ACTIVATION_TEMPLATE_PATH = "classpath:templates/activation-email.html";
    private static final String EMAIL_CHANGE_TEMPLATE_PATH = "classpath:templates/email-change-email.html";
    private final String activationTemplateContent;
    private final String emailChangeTemplateContent;

    public EmailTemplateService(ResourceLoader resourceLoader) {
        this.activationTemplateContent = loadTemplate(resourceLoader, ACTIVATION_TEMPLATE_PATH);
        this.emailChangeTemplateContent = loadTemplate(resourceLoader, EMAIL_CHANGE_TEMPLATE_PATH);
    }

    @Override
    public String buildActivationEmail(String fullName, String activationLink) {
        return activationTemplateContent
                .replace("{{FULL_NAME}}", sanitize(fullName))
                .replace("{{ACTIVATION_LINK}}", activationLink);
    }

    @Override
    public String buildEmailChangeEmail(String fullName, String currentEmail, String newEmail, String confirmationLink) {
        return emailChangeTemplateContent
                .replace("{{FULL_NAME}}", sanitize(fullName))
                .replace("{{CURRENT_EMAIL}}", sanitize(currentEmail))
                .replace("{{NEW_EMAIL}}", sanitize(newEmail))
                .replace("{{ACTIVATION_LINK}}", confirmationLink);
    }

    private String loadTemplate(ResourceLoader resourceLoader, String path) {
        Resource resource = resourceLoader.getResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Could not read email template from {}", path, ex);
            throw new IllegalStateException("Email template could not be loaded: " + path, ex);
        }
    }

    private String sanitize(String value) {
        return value == null ? "" : value.trim();
    }
}
