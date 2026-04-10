package com.example.auth.service;

import java.util.UUID;

public interface IVerificationTokenService {
    /**
     * Issue a new verification token for the given account and email.
     * If a previous unused token exists, it will be replaced.
     * If email is null/blank, the account's primary email will be used.
     * @param accountId the account ID
     * @param email the email to verify (optional, falls back to account's email)
     * @return the raw token (for sending in email)
     */
    String issueToken(UUID accountId, String email);

    /**
     * Confirm email verification by consuming a token.
     * @param token the raw verification token
     */
    void confirmEmail(String token);

    /**
     * Get the verification status for an account's primary email.
     * @param accountId the account ID
     * @return true if email is verified, false otherwise
     */
    boolean isEmailVerified(UUID accountId);

    /**
     * Get the primary email for an account (from Account entity if verified).
     * @param accountId the account ID
     * @return the email if verified, null otherwise
     */
    String getVerifiedEmail(UUID accountId);
}
