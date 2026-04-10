package com.example.auth.service;

import java.util.UUID;

public interface IPasswordService {
    void changePassword(UUID accountId, String oldPass, String newPass);
}
