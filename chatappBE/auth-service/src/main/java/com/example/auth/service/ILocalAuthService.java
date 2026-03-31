package com.example.auth.service;

import java.util.UUID;

public interface ILocalAuthService {
    UUID register(String email, String password);
    UUID login(String email, String password);


}
