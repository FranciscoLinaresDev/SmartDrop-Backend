
package com.smart.drop.iam.application.services;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.datasource.url=jdbc:h2:mem:smartdrop_2fa_transaction_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "smartdrop.two-factor.enabled=false"
})
@Import(TwoFactorChallengeTransactionTest.TestConfig.class)
class TwoFactorChallengeTransactionTest {

    @Autowired
    private TwoFactorChallengeService service;

    @Autowired
    private TwoFactorChallengeJpaRepository challenges;

    @Autowired
    private UserJpaRepository users;

    @Autowired
    private TwoFactorCodeSender sender;

    @DynamicPropertySource
    static void configureJwt(DynamicPropertyRegistry registry) {
        registry.add(
                "authorization.jwt.secret",
                TwoFactorChallengeTransactionTest::randomSecret
        );
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // TEST 1: Failed attempts must persist in the database.

    @Test
    void failedAttemptsShouldPersistAcrossTransactions() {

        var setup = createChallenge();

        String wrongCode = setup.code().equals("000000")
                ? "999999"
                : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {

            var result = service.verify(
                    setup.challengeId(),
                    wrongCode
            );

            assertFalse(result.verified());

            var stored = challenges
                    .findById(setup.challengeId())
                    .orElseThrow();

            assertEquals(
                    attempt,
                    stored.getFailedAttempts()
            );
        }

        assertFalse(
                service.verify(
                        setup.challengeId(),
                        setup.code()
                ).verified()
        );
    }

    // TEST 2: A successful code can only be used once.

    @Test
    void successfulCodeShouldBeConsumedOnlyOnce() {

        var setup = createChallenge();

        var first = service.verify(
                setup.challengeId(),
                setup.code()
        );

        assertTrue(first.verified());
        assertEquals(setup.userId(), first.userId());

        var stored = challenges
                .findById(setup.challengeId())
                .orElseThrow();

        assertNotNull(stored.getConsumedAt());

        var second = service.verify(
                setup.challengeId(),
                setup.code()
        );

        assertFalse(second.verified());
    }

    // TEST 3: Reject a second challenge while one is active.

    @Test
    void shouldRejectDuplicateActiveChallenge() {

        var setup = createChallenge();

        String email = users.findById(setup.userId())
                .orElseThrow()
                .getEmail();

        assertThrows(
                IllegalStateException.class,
                () -> service.createAfterPasswordVerified(
                        setup.userId(),
                        email
                )
        );

        verify(sender, times(1)).sendCode(
                eq(email),
                anyString()
        );
    }

    // TEST 4: Enforce cooldown after successful verification.

    @Test
    void shouldEnforceCooldownAfterSuccessfulVerification() {

        var setup = createChallenge();

        var result = service.verify(
                setup.challengeId(),
                setup.code()
        );

        assertTrue(result.verified());

        String email = users.findById(setup.userId())
                .orElseThrow()
                .getEmail();

        assertThrows(
                IllegalStateException.class,
                () -> service.createAfterPasswordVerified(
                        setup.userId(),
                        email
                )
        );

        verify(sender, times(1)).sendCode(
                eq(email),
                anyString()
        );
    }

    // TEST 5: Never send codes to an unauthorized email.

    @Test
    void shouldRejectSendingCodeToDifferentEmail() {

        var setup = createChallenge();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.createAfterPasswordVerified(
                        setup.userId(),
                        "attacker@example.com"
                )
        );

        verify(sender, never()).sendCode(
                eq("attacker@example.com"),
                anyString()
        );
    }


@Test
void concurrentRequestsShouldCreateOnlyOneChallenge()
        throws Exception {

    String email = "concurrent-"
            + UUID.randomUUID()
            + "@example.com";

    UserEntity user = users.saveAndFlush(
            new UserEntity(
                    "Concurrent Test User",
                    email,
                    "test-hash"
            )
    );

    reset(sender);

    ExecutorService executor =
            Executors.newFixedThreadPool(2);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    Callable<Boolean> requestChallenge = () -> {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                    "Concurrent test start timed out"
            );
        }

        try {
            service.createAfterPasswordVerified(
                    user.getUserId(),
                    email
            );

            return true;

        } catch (IllegalStateException exception) {

            String message = exception.getMessage();

            if (message != null &&
                    (message.contains("active verification challenge")
                    || message.contains("wait before requesting"))) {
                return false;
            }

            throw exception;
        }
    };

    try {
        Future<Boolean> first =
                executor.submit(requestChallenge);

        Future<Boolean> second =
                executor.submit(requestChallenge);

        assertTrue(
                ready.await(10, TimeUnit.SECONDS),
                "Both requests must be ready"
        );

        start.countDown();

        boolean firstResult =
                first.get(20, TimeUnit.SECONDS);

        boolean secondResult =
                second.get(20, TimeUnit.SECONDS);

        int successfulRequests =
                (firstResult ? 1 : 0)
                        + (secondResult ? 1 : 0);

        assertEquals(
                1,
                successfulRequests,
                "Only one request should create a challenge"
        );

        verify(sender, times(1)).sendCode(
                eq(email),
                anyString()
        );

        assertTrue(
                challenges.existsActiveForUser(
                        user.getUserId(),
                        java.time.LocalDateTime.now(),
                        5
                )
        );

    } finally {
        start.countDown();
        executor.shutdownNow();
    }
}


    private ChallengeSetup createChallenge() {

        String email = "test-"
                + UUID.randomUUID()
                + "@example.com";

        UserEntity user = users.saveAndFlush(
                new UserEntity(
                        "Security Test",
                        email,
                        "test-hash"
                )
        );

        reset(sender);

        String challengeId =
                service.createAfterPasswordVerified(
                        user.getUserId(),
                        email
                );

        ArgumentCaptor<String> codeCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(sender).sendCode(
                eq(email),
                codeCaptor.capture()
        );

        return new ChallengeSetup(
                user.getUserId(),
                challengeId,
                codeCaptor.getValue()
        );
    }

    private record ChallengeSetup(
            Integer userId,
            String challengeId,
            String code
    ) {
    }

    // TEST CONFIGURATION: Real JPA, mocked email delivery.

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        TwoFactorCodeSender testCodeSender() {
            return mock(TwoFactorCodeSender.class);
        }

        @Bean
        TwoFactorCodeHasher testCodeHasher() {
            return new TwoFactorCodeHasher(randomSecret());
        }

        @Bean
        TwoFactorChallengeService testChallengeService(
                TwoFactorChallengeJpaRepository repository,
                UserJpaRepository userRepository,
                TwoFactorCodeGenerator generator,
                TwoFactorCodeHasher hasher,
                TwoFactorCodeSender sender) {

            return new TwoFactorChallengeService(
                    repository,
                    userRepository,
                    generator,
                    hasher,
                    sender,
                    Clock.systemUTC()
            );
        }
    }
}
