
package com.smart.drop.iam.application.services;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwoFactorChallengeServiceTest {

    private TwoFactorChallengeJpaRepository repository;
    private TwoFactorCodeGenerator generator;
    private TwoFactorCodeSender sender;
    private TwoFactorCodeHasher hasher;
    private TwoFactorChallengeService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        repository = mock(TwoFactorChallengeJpaRepository.class);
        generator = mock(TwoFactorCodeGenerator.class);
        sender = mock(TwoFactorCodeSender.class);

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        hasher = new TwoFactorCodeHasher(
                Base64.getEncoder().encodeToString(key)
        );

        clock = Clock.fixed(
                Instant.parse("2026-10-15T12:00:00Z"),
                ZoneOffset.UTC
        );

        service = new TwoFactorChallengeService(
                repository,
                generator,
                hasher,
                sender,
                clock
        );

        when(generator.generateCode()).thenReturn("123456");
    }

    @Test
    void shouldCreateChallengeWithProtectedCode() {
        TwoFactorChallengeEntity challenge = createChallenge();

        assertNotNull(challenge.getChallengeId());
        assertEquals(42, challenge.getUserId());
        assertEquals(0, challenge.getFailedAttempts());

        assertNotEquals("123456", challenge.getCodeHash());

        assertTrue(hasher.matches(
                42, "123456", challenge.getCodeHash()
        ));

        verify(sender).sendCode(
                "test@example.com", "123456"
        );
    }

    @Test
    void shouldVerifyAndConsumeChallengeOnlyOnce() {
        TwoFactorChallengeEntity challenge = createChallenge();

        var first = service.verify(
                challenge.getChallengeId(), "123456"
        );

        assertTrue(first.verified());
        assertEquals(42, first.userId());
        assertNotNull(challenge.getConsumedAt());

        var second = service.verify(
                challenge.getChallengeId(), "123456"
        );

        assertFalse(second.verified());
        assertNull(second.userId());
    }

    @Test
    void shouldBlockAfterFiveFailedAttempts() {
        TwoFactorChallengeEntity challenge = createChallenge();

        for (int i = 0; i < 5; i++) {
            var result = service.verify(
                    challenge.getChallengeId(), "654321"
            );

            assertFalse(result.verified());
        }

        assertEquals(5, challenge.getFailedAttempts());

        var result = service.verify(
                challenge.getChallengeId(), "123456"
        );

        assertFalse(result.verified());
        assertNull(challenge.getConsumedAt());
    }

    @Test
    void shouldRejectExpiredChallenge() {
        TwoFactorChallengeEntity challenge = createChallenge();

        Clock futureClock = Clock.offset(
                clock, Duration.ofMinutes(6)
        );

        TwoFactorChallengeService expiredService =
                new TwoFactorChallengeService(
                        repository,
                        generator,
                        hasher,
                        sender,
                        futureClock
                );

        var result = expiredService.verify(
                challenge.getChallengeId(), "123456"
        );

        assertFalse(result.verified());
        assertNull(challenge.getConsumedAt());
        assertEquals(0, challenge.getFailedAttempts());
    }

    private TwoFactorChallengeEntity createChallenge() {
        String challengeId =
                service.createAfterPasswordVerified(
                        42, "test@example.com"
                );

        ArgumentCaptor<TwoFactorChallengeEntity> captor =
                ArgumentCaptor.forClass(
                        TwoFactorChallengeEntity.class
                );

        verify(repository).save(captor.capture());

        TwoFactorChallengeEntity challenge =
                captor.getValue();

        assertEquals(
                challengeId,
                challenge.getChallengeId()
        );

        when(repository.findByIdForUpdate(challengeId))
                .thenReturn(Optional.of(challenge));

        return challenge;
    }
}
