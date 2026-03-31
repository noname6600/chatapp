package com.example.auth.controller;

import com.example.auth.dto.*;
import com.example.common.web.controller.BaseController;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.common.web.response.ApiResponse;
import com.example.auth.service.IAuthService;
import com.example.auth.service.impl.AuthService;
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
}

