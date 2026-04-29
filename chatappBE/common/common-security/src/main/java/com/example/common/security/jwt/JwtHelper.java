package com.example.common.security.jwt;

import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public final class JwtHelper {

    private JwtHelper() {
    }

    public static UUID extractUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized");
        }

        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized");
        }
    }
}
