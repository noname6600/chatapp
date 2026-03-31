package com.example.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;

public interface IGoogleTokenVerifier {
    GoogleIdToken.Payload verify(String idTokenString);
}
