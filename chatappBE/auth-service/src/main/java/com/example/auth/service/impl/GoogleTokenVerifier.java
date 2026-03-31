package com.example.auth.service.impl;

import com.example.auth.service.IGoogleTokenVerifier;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Service
@Slf4j
public class GoogleTokenVerifier implements IGoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(
            @Value("${security.oauth2.client.registration.google.client-id}")
            String clientId
    ) {

        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(clientId))
                .build();
    }

    @Override
    public GoogleIdToken.Payload verify(String idTokenString) {

        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);

            if (idToken == null) {
                log.warn("Google ID token verification returned null");
                throw new BusinessException(
                        ErrorCode.UNAUTHORIZED,
                        "Invalid Google ID token"
                );
            }

            return idToken.getPayload();

        } catch (GeneralSecurityException | IOException ex) {

            log.warn("Google ID token verification failed: {}", ex.getMessage());

            throw new BusinessException(
                    ErrorCode.UNAUTHORIZED,
                    "Invalid Google ID token"
            );

        } catch (Exception ex) {

            log.error("Unexpected error during Google token verification", ex);

            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Google token verification failed"
            );
        }
    }
}

