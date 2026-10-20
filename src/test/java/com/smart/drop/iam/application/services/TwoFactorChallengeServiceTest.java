
package com.smart.drop.iam.application.services;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TwoFactorChallengeServiceTest {

    private TwoFactorChallengeJpaRepository repository;
    private UserJpaRepository userRepository;

    private TwoFactorCodeGenerator generator;
    private TwoFactorCodeSender sender;
    private TwoFactorCodeHasher hasher;

    private TwoFactorChallengeService service;
    private Clock clock;

    @BeforeEach
    void setUp() {

        repository = mock(TwoFactorChallengeJpaRepository.class);
        userRepository = mock(UserJpaRepository.class);

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

        UserEntity user = new UserEntity(
                "Test User",
                "test@example.com",
                "test-hash"
        );

        when(userRepository.findByIdForUpdate(42))
                .thenReturn(Optional.of(user));

        when(generator.generateCode())
                .thenReturn("123456");

        service = new TwoFactorChallengeService(
                repository,
                userRepository,
                generator,
                hasher,
                sender,
                clock
        );
    }

    @Test
    void shouldCreateChallengeWithProtectedCode() {

        TwoFactorChallengeEntity challenge = createChallenge();

        assertNotNull(challenge.getChallengeId());
        assertEquals(42, challenge.getUserId());
        assertEquals(0, challenge.getFailedAttempts());

        assertNotEquals("123456", challenge.getCodeHash());

        assertTrue(
                hasher.matches(
                        42,
                        "123456",
                        challenge.getCodeHash()
                )
        );

        verify(sender).sendCode(
                "test@example.com",
                "123456"
        );

        verify(userRepository).findByIdForUpdate(42);
    }

    @Test
    void shouldVerifyAndConsumeChallengeOnlyOnce() {

        TwoFactorChallengeEntity challenge = createChallenge();

        var first = service.verify(
                challenge.getChallengeId(),
                "123456"
        );

        assertTrue(first.verified());
        assertEquals(42, first.userId());
        assertNotNull(challenge.getConsumedAt());

        var second = service.verify(
                challenge.getChallengeId(),
                "123456"
        );

        assertFalse(second.verified());
        assertNull(second.userId());
    }

    @Test
    void shouldBlockAfterFiveFailedAttempts() {

        TwoFactorChallengeEntity challenge = createChallenge();

        for (int i = 0; i < 5; i++) {

            var result = service.verify(
                    challenge.getChallengeId(),
                    "654321"
            );

            assertFalse(result.verified());
        }

        assertEquals(
                5,
                challenge.getFailedAttempts()
        );

        var result = service.verify(
                challenge.getChallengeId(),
                "123456"
        );

        assertFalse(result.verified());
        assertNull(challenge.getConsumedAt());
    }

    @Test
    void shouldRejectExpiredChallenge() {

        TwoFactorChallengeEntity challenge = createChallenge();

        Clock futureClock = Clock.offset(
                clock,
                Duration.ofMinutes(6)
        );

        TwoFactorChallengeService expiredService =
                new TwoFactorChallengeService(
                        repository,
                        userRepository,
                        generator,
                        hasher,
                        sender,
                        futureClock
                );

        var result = expiredService.verify(
                challenge.getChallengeId(),
                "123456"
        );

        assertFalse(result.verified());
        assertNull(challenge.getConsumedAt());
        assertEquals(0, challenge.getFailedAttempts());
    }

    @Test
    void shouldRejectDuplicateActiveChallenge() {

        when(repository.existsActiveForUser(
                eq(42),
                any(LocalDateTime.class),
                eq(5)
        )).thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> service.createAfterPasswordVerified(
                        42,
                        "test@example.com"
                )
        );

        verify(repository, never()).save(
                any(TwoFactorChallengeEntity.class)
        );

        verifyNoInteractions(sender);
    }

    @Test
    void shouldRejectChallengeDuringCooldown() {

        LocalDateTime now = LocalDateTime.now(clock);

        TwoFactorChallengeEntity recentChallenge =
                TwoFactorChallengeEntity.create(
                        42,
                        hasher.hash(42, "123456"),
                        now.minusSeconds(30),
                        now.plusMinutes(4)
                );

        when(repository.findTopByUserIdOrderByCreatedAtDesc(42))
                .thenReturn(Optional.of(recentChallenge));

        assertThrows(
                IllegalStateException.class,
                () -> service.createAfterPasswordVerified(
                        42,
                        "test@example.com"
                )
        );

        verify(repository, never()).save(
                any(TwoFactorChallengeEntity.class)
        );

        verifyNoInteractions(sender);
    }

    private TwoFactorChallengeEntity createChallenge() {

        String challengeId =
                service.createAfterPasswordVerified(
                        42,
                        "test@example.com"
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
