package com.chatweb.auth.service;

import java.util.UUID;

public interface IPasswordService {
    void changePassword(UUID accountId, String oldPass, String newPass);
    void setPassword(UUID accountId, String newPass);
}
