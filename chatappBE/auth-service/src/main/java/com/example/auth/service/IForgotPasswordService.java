package com.example.auth.service;

import java.util.UUID;

public interface IForgotPasswordService {
    /**
     * Request a password reset for an account with the given email.
     * Response is always consistent (no email-existence leakage).
     * @param email the account's email address
     */
    void requestReset(String email);

    /**
     * Confirm a password reset using the one-time token from email.
     * @param token the raw reset token
     * @param newPassword the new password to set
     */
    void resetPassword(String token, String newPassword);
}
