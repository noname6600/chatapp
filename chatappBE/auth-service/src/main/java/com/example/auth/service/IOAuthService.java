package com.example.auth.service;

import java.util.UUID;

public interface IOAuthService {
    UUID loginGoogle(String googleSub, String email);

}
