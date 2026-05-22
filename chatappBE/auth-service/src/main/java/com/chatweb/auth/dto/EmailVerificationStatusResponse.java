package com.chatweb.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationStatusResponse {
    private String email;
    private boolean verified;
    private boolean hasPassword;
}
