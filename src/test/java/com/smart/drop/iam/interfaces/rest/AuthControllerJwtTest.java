package com.smart.drop.iam.interfaces.rest;

import com.smart.drop.iam.application.services.AuthService;
import com.smart.drop.iam.application.services.TwoFactorChallengeService;
import com.smart.drop.iam.domain.exceptions.TwoFactorRequiredException;
import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.interfaces.rest.dto.LoginResponseDto;
import com.smart.drop.iam.interfaces.rest.dto.LoginUserRequestDto;
import com.smart.drop.iam.interfaces.rest.dto.VerifyTwoFactorChallengeRequestDto;
import com.smart.drop.shared.infrastructure.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthControllerJwtTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "Password123!";
    private static final String CHALLENGE_ID = "challenge-abc";

    private AuthService authService;
    private JwtService jwtService;
    private TwoFactorChallengeService challengeService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        jwtService = mock(JwtService.class);
        challengeService = mock(TwoFactorChallengeService.class);

        controller = new AuthController(authService, jwtService);
        // New 2FA is disabled by default. Tests that need it enable the mock explicitly.
    }

    @Test
    void shouldReturnJwtAfterSuccessfulLoginWithoutTwoFactor() {
        when(authService.loginUser(EMAIL, PASSWORD))
                .thenReturn(createUser(false));
        when(jwtService.createAccessToken(42, EMAIL))
                .thenReturn("direct-access-token");

        var response = controller.loginUser(new LoginUserRequestDto(EMAIL, PASSWORD));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = assertInstanceOf(LoginResponseDto.class, response.getBody());
        assertEquals(42, body.userId());
        assertEquals(EMAIL, body.email());
        assertEquals("direct-access-token", body.accessToken());
        assertEquals("Bearer", body.tokenType());
        verify(jwtService).createAccessToken(42, EMAIL);
        verifyNoInteractions(challengeService);
    }

    @Test
    void shouldNotIssueJwtWhenTwoFactorServiceIsUnavailable() {
        mockPasswordValidatedButTwoFactorRequired();

        var response = controller.loginUser(new LoginUserRequestDto(EMAIL, PASSWORD));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertFalse(body.containsKey("accessToken"));
        assertFalse(body.containsKey("challengeId"));
        assertFalse(body.containsKey("simulatedCode"));
        verifyNoInteractions(jwtService, challengeService);
    }

    @Test
    void shouldReturnChallengeIdButNoJwtWhenTwoFactorIsAvailable() {
        enableTwoFactorService();
        mockPasswordValidatedButTwoFactorRequired();
        when(authService.getUserByEmail(EMAIL))
                .thenReturn(Optional.of(createUser(true)));
        when(challengeService.createAfterPasswordVerified(42, EMAIL))
                .thenReturn(CHALLENGE_ID);

        var response = controller.loginUser(new LoginUserRequestDto(EMAIL, PASSWORD));

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        Map<?, ?> body = assertInstanceOf(Map.class, response.getBody());
        assertEquals(Boolean.TRUE, body.get("requires2FA"));
        assertEquals(CHALLENGE_ID, body.get("challengeId"));
        assertFalse(body.containsKey("accessToken"));
        assertFalse(body.containsKey("simulatedCode"));
        verify(challengeService).createAfterPasswordVerified(42, EMAIL);
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldNotIssueJwtWhenAccountTwoFactorStateChanged() {
        enableTwoFactorService();
        mockPasswordValidatedButTwoFactorRequired();
        when(authService.getUserByEmail(EMAIL))
                .thenReturn(Optional.of(createUser(false)));

        var response = controller.loginUser(new LoginUserRequestDto(EMAIL, PASSWORD));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verifyNoInteractions(challengeService, jwtService);
    }

    @Test
    void shouldRateLimitDuplicateChallengeWithoutIssuingJwt() {
        enableTwoFactorService();
        mockPasswordValidatedButTwoFactorRequired();
        when(authService.getUserByEmail(EMAIL))
                .thenReturn(Optional.of(createUser(true)));
        when(challengeService.createAfterPasswordVerified(42, EMAIL))
                .thenThrow(new IllegalStateException("Active challenge exists"));

        var response = controller.loginUser(new LoginUserRequestDto(EMAIL, PASSWORD));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldNotIssueJwtForInvalidCredentials() {
        when(authService.loginUser(EMAIL, "WrongPassword"))
                .thenThrow(new IllegalArgumentException("Invalid credentials"));

        assertThrows(IllegalArgumentException.class,
                () -> controller.loginUser(
                        new LoginUserRequestDto(EMAIL, "WrongPassword")));

        verifyNoInteractions(jwtService, challengeService);
    }

    @Test
    void shouldRejectVerificationWhenTwoFactorServiceIsUnavailable() {
        var response = controller.verify2FA(
                new VerifyTwoFactorChallengeRequestDto(CHALLENGE_ID, "123456"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        verifyNoInteractions(jwtService, challengeService);
    }

    @Test
    void shouldRejectMalformedVerificationRequest() {
        enableTwoFactorService();

        var response = controller.verify2FA(
                new VerifyTwoFactorChallengeRequestDto(CHALLENGE_ID, "12"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(jwtService, challengeService);
    }

    @Test
    void shouldNotIssueJwtForInvalidOrExpiredVerificationCode() {
        enableTwoFactorService();
        when(challengeService.verify(CHALLENGE_ID, "654321"))
                .thenReturn(new TwoFactorChallengeService.VerificationResult(false, null));

        var response = controller.verify2FA(
                new VerifyTwoFactorChallengeRequestDto(CHALLENGE_ID, "654321"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(jwtService);
        verify(authService, never()).getUser(anyInt());
    }

    @Test
    void shouldReturnJwtOnlyAfterSuccessfulTwoFactorVerification() {
        enableTwoFactorService();
        when(challengeService.verify(CHALLENGE_ID, "123456"))
                .thenReturn(new TwoFactorChallengeService.VerificationResult(true, 42));
        when(authService.getUser(42))
                .thenReturn(Optional.of(createUser(true)));
        when(jwtService.createAccessToken(42, EMAIL))
                .thenReturn("verified-access-token");

        var response = controller.verify2FA(
                new VerifyTwoFactorChallengeRequestDto(CHALLENGE_ID, "123456"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        LoginResponseDto body = assertInstanceOf(LoginResponseDto.class, response.getBody());
        assertEquals(42, body.userId());
        assertEquals(EMAIL, body.email());
        assertEquals("verified-access-token", body.accessToken());
        assertEquals("Bearer", body.tokenType());
        verify(challengeService).verify(CHALLENGE_ID, "123456");
        verify(jwtService).createAccessToken(42, EMAIL);
    }

    @Test
    void shouldNotIssueJwtIfAccountIsNoLongerEligibleForTwoFactor() {
        enableTwoFactorService();
        when(challengeService.verify(CHALLENGE_ID, "123456"))
                .thenReturn(new TwoFactorChallengeService.VerificationResult(true, 42));
        when(authService.getUser(42))
                .thenReturn(Optional.of(createUser(false)));

        var response = controller.verify2FA(
                new VerifyTwoFactorChallengeRequestDto(CHALLENGE_ID, "123456"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(jwtService);
    }

    private void enableTwoFactorService() {
        controller.setTwoFactorChallengeService(challengeService);
    }

    private void mockPasswordValidatedButTwoFactorRequired() {
        when(authService.loginUser(EMAIL, PASSWORD))
                .thenThrow(new TwoFactorRequiredException(EMAIL, ""));
    }

    private User createUser(boolean twoFactorEnabled) {
        return User.createFromDatabase(
                42,
                "Test User",
                EMAIL,
                "hashed-password",
                new HashSet<>(),
                LocalDateTime.now(),
                null,
                twoFactorEnabled,
                null
        );
    }
}
