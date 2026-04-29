package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.common.web.controller.BaseController;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.common.web.response.ApiResponse;
import com.example.auth.service.IAuthService;
import com.example.auth.service.IForgotPasswordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final IAuthService authService;
    private final IForgotPasswordService forgotPasswordService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response =
                authService.register(request.getUsername(), request.getPassword());
        return created(response);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse response =
                authService.login(request.getUsername(), request.getPassword());
        return ok(response);
    }

    @PostMapping("/login/google")
    public ResponseEntity<ApiResponse<AuthResponse>> loginGoogle(
            @Valid @RequestBody GoogleLoginRequest request
    ) {
        AuthResponse response =
                authService.loginGoogle(request.getIdToken());
        return ok(response);
    }

    @PostMapping("/oauth/google/exchange")
    public ResponseEntity<ApiResponse<AuthResponse>> exchangeGoogleOAuthCode(
            @Valid @RequestBody OAuthExchangeRequest request
    ) {
        AuthResponse response = authService.exchangeGoogleOAuthCode(request.getCode());
        return ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshRequest request
    ) {
        AuthResponse response =
                authService.refresh(request.getRefreshToken());
        return ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshRequest request
    ) {
        authService.logout(request.getRefreshToken());
        return ok();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        authService.logoutAll(principal.getAccountId());
        return ok();
    }

    @PostMapping("/password/change")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        if (principal == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        authService.changePassword(
                principal.getAccountId(),
                request.getCurrentPassword(),
                request.getNewPassword()
        );
        return ok();
    }

    @GetMapping("/email/verification/status")
    public ResponseEntity<ApiResponse<EmailVerificationStatusResponse>> getEmailVerificationStatus(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        EmailVerificationStatusResponse response = authService.getEmailVerificationStatus(principal.getAccountId());
        return ok(response);
    }

    @PostMapping("/email/verification/send")
    public ResponseEntity<ApiResponse<Void>> sendVerificationEmail(
            @AuthenticationPrincipal JwtPrincipal principal
    ) {
        authService.sendVerificationEmail(principal.getAccountId());
        return ok();
    }

    @PostMapping("/email/verification/confirm")
    public ResponseEntity<ApiResponse<Void>> confirmEmail(
            @Valid @RequestBody EmailVerificationConfirmRequest request
    ) {
        authService.confirmEmail(request.getToken());
        return ok();
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        forgotPasswordService.requestReset(request.getEmail());
        return ok(); // always 200 to avoid email existence leakage
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        forgotPasswordService.resetPassword(request.getToken(), request.getNewPassword());
        return ok();
    }
}



