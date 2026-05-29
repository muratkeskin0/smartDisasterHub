package com.caglamurat.smartDisasterHub.service.mail;

import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailService implements IEmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Override
    public boolean isConfigured() {
        return mailPassword != null && !mailPassword.isBlank();
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        if (!isConfigured()) {
            log.warn("SMTP password not configured; skipping email to {}", to);
            throw new BusinessException(
                    ErrorCode.EMAIL_DELIVERY_FAILED,
                    "Email delivery is not configured on this server. Contact the administrator."
            );
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException ex) {
            log.error("Failed to build email for {}", to, ex);
            throw new BusinessException(
                    ErrorCode.EMAIL_DELIVERY_FAILED,
                    "Could not prepare the activation email. Please try again later."
            );
        } catch (MailException ex) {
            log.error("Failed to send email to {}", to, ex);
            throw new BusinessException(
                    ErrorCode.EMAIL_DELIVERY_FAILED,
                    "Could not send the activation email. The mail server rejected the request."
            );
        }
    }
}
