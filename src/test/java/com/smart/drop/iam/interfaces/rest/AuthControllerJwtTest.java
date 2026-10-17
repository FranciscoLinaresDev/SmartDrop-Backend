
package com.smart.drop.iam.interfaces.rest;

import com.smart.drop.iam.application.services.AuthService;
import com.smart.drop.iam.domain.exceptions.TwoFactorRequiredException;
import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.interfaces.rest.dto.LoginResponseDto;
import com.smart.drop.iam.interfaces.rest.dto.LoginUserRequestDto;
import com.smart.drop.shared.infrastructure.security.JwtService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerJwtTest {

    private AuthService authService;
    private JwtService jwtService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);

        controller = new AuthController(authService, jwtService);
    }

    @Test
    void shouldReturnJwtAfterSuccessfulLogin() {

        User user = createUser();

        when(authService.loginUser(
                "test@example.com",
                "Password123!"
        )).thenReturn(user);

        when(jwtService.createAccessToken(
                42,
                "test@example.com"
        )).thenReturn("test-access-token");

        var request = new LoginUserRequestDto(
                "test@example.com",
                "Password123!"
        );

        var response = controller.loginUser(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        LoginResponseDto body = assertInstanceOf(
                LoginResponseDto.class,
                response.getBody()
        );

        assertEquals(42, body.userId());
        assertEquals("test@example.com", body.email());
        assertEquals("test-access-token", body.accessToken());
        assertEquals("Bearer", body.tokenType());

        verify(jwtService).createAccessToken(
                42,
                "test@example.com"
        );
    }

    @Test
    void shouldNotIssueJwtBeforeTwoFactorVerification() {

        when(authService.loginUser(
                "test@example.com",
                "Password123!"
        )).thenThrow(
                new TwoFactorRequiredException(
                        "test@example.com",
                        "123456"
                )
        );

        var request = new LoginUserRequestDto(
                "test@example.com",
                "Password123!"
        );

        var response = controller.loginUser(request);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        Map<?, ?> body = assertInstanceOf(
                Map.class,
                response.getBody()
        );

        assertNull(body.get("requires2FA"));
        assertFalse(body.containsKey("accessToken"));
        assertFalse(body.containsKey("simulatedCode"));
        assertFalse(body.toString().contains("123456"));

        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldNotIssueJwtForInvalidCredentials() {

        when(authService.loginUser(
                "test@example.com",
                "WrongPassword"
        )).thenThrow(
                new IllegalArgumentException("Invalid credentials")
        );

        var request = new LoginUserRequestDto(
                "test@example.com",
                "WrongPassword"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> controller.loginUser(request)
        );

        verifyNoInteractions(jwtService);
    }

    private User createUser() {

        return User.createFromDatabase(
                42,
                "Test User",
                "test@example.com",
                "hashed-password",
                new HashSet<>(),
                LocalDateTime.now(),
                null,
                false,
                null
        );
    }
}
