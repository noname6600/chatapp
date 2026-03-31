package com.example.auth.service;

public interface IPasswordService {
    void changePassword(String accountId, String oldPass, String newPass);
}
