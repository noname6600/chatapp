package com.example.auth.service.impl;

import com.example.auth.repository.AccountRepository;
import com.example.auth.service.IPasswordService;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PasswordService implements IPasswordService {

    private final PasswordEncoder passwordEncoder;
    private final AccountRepository accountRepository;
    @Override
    public void changePassword(String accountId, String oldPass, String newPass) {

    }
}
