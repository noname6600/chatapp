package com.example.auth.controller;

import com.example.auth.dto.ChangePasswordRequest;
import com.example.auth.dto.LoginRequest;
import com.example.auth.dto.RegisterRequest;
import com.example.auth.dto.AuthResponse;
import com.example.auth.dto.JwtPrincipal;
import com.example.auth.service.IAuthService;
import com.example.auth.service.IForgotPasswordService;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    private IAuthService authService;
    private IForgotPasswordService forgotPasswordService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(IAuthService.class);
        forgotPasswordService = mock(IForgotPasswordService.class);
        controller = new AuthController(authService, forgotPasswordService);
    }

    @Test
    void changePassword_throwsUnauthorized_whenPrincipalIsNull() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass1");
        request.setNewPassword("NewPass2!");

        assertThatThrownBy(() -> controller.changePassword(null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(authService);
    }

    @Test
    void changePassword_delegatesToService_whenPrincipalPresent() {
        UUID accountId = UUID.randomUUID();
        JwtPrincipal principal = new JwtPrincipal(accountId);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("OldPass1");
        request.setNewPassword("NewPass2!");

        var result = controller.changePassword(principal, request);

        verify(authService).changePassword(accountId, "OldPass1", "NewPass2!");
        assertThat(result.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void login_returnsNonEmptyAccessAndRefreshTokens() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice@example.com");
        request.setPassword("Password1!");

        when(authService.login("alice@example.com", "Password1!"))
                .thenReturn(new AuthResponse("access-token", "refresh-token", 900));

        var response = controller.login(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getData().getRefreshToken()).isNotBlank();
        verify(authService).login("alice@example.com", "Password1!");
    }

    @Test
    void register_returnsNonEmptyAccessAndRefreshTokens() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob@example.com");
        request.setPassword("Password1!");

        when(authService.register("bob@example.com", "Password1!"))
                .thenReturn(new AuthResponse("access-token", "refresh-token", 900));

        var response = controller.register(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getAccessToken()).isNotBlank();
        assertThat(response.getBody().getData().getRefreshToken()).isNotBlank();
        verify(authService).register("bob@example.com", "Password1!");
    }
}
