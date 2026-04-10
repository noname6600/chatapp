package com.example.auth.service;

import java.util.UUID;

public interface IEmailService {
    /**
     * Send a verification email to the account's email address.
     * @param accountId the account ID
     * @param verificationToken the raw verification token to include in email
     */
    void sendVerificationEmail(UUID accountId, String verificationToken);

    /**
     * Send a password reset email to the account's email address.
     * @param accountId the account ID
     * @param resetToken the raw reset token to include in email
     */
    void sendPasswordResetEmail(UUID accountId, String resetToken);
}
