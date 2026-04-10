package com.example.auth.service.impl;

import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IEmailService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService implements IEmailService {

    private final JavaMailSender mailSender;
    private final AccountRepository accountRepository;

    @Value("${app.frontend.reset-password-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${spring.mail.username:noreply@chatapp.local}")
    private String fromEmail;

    @Override
    public void sendVerificationEmail(UUID accountId, String verificationToken) {
        String email = accountRepository.findById(accountId)
                .map(account -> account.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Account not found"));

        String confirmUrl = frontendUrl + "/auth/verify-email?token=" + verificationToken;
        String subject = "Verify Your Email - ChatApp";
        String body = String.format(
                "Please verify your email by clicking the link below:\n\n%s\n\nThis link will expire in 24 hours.",
                confirmUrl
        );

        sendEmail(email, subject, body);
    }

    @Override
    public void sendPasswordResetEmail(UUID accountId, String resetToken) {
        String email = accountRepository.findById(accountId)
                .map(account -> account.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Account not found"));

        String resetUrl = frontendUrl + "/auth/reset-password?token=" + resetToken;
        String subject = "Reset Your Password - ChatApp";
        String body = String.format(
                "Click the link below to reset your password:\n\n%s\n\nThis link will expire in 1 hour.",
                resetUrl
        );

        sendEmail(email, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Failed to send verification email. Please try again later."
            );
        }
    }
}
