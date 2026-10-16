
package com.smart.drop.iam.infrastructure.persistence.jpa.repositories;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.datasource.url=jdbc:h2:mem:smartdrop_2fa_repository_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class TwoFactorChallengeJpaRepositoryTest {

    @Autowired
    private TwoFactorChallengeJpaRepository challengeRepository;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureJwt(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String secret = Base64.getEncoder().encodeToString(key);

        registry.add(
                "authorization.jwt.secret",
                () -> secret
        );

        registry.add(
                "identity.token.jwt-secret",
                () -> secret
        );
    }

    @Test
    void shouldPersistRetrieveAndUpdateChallenge() {

        UserEntity user = new UserEntity(
                "Security Test User",
                "security-" + UUID.randomUUID() + "@example.com",
                "test-hash"
        );

        user = userRepository.saveAndFlush(user);

        LocalDateTime now = LocalDateTime.now();

        TwoFactorChallengeEntity challenge =
                TwoFactorChallengeEntity.create(
                        user.getUserId(),
                        "a".repeat(64),
                        now,
                        now.plusMinutes(5)
                );

        challengeRepository.saveAndFlush(challenge);

        String challengeId = challenge.getChallengeId();

        entityManager.clear();

        TwoFactorChallengeEntity stored =
                challengeRepository.findByIdForUpdate(challengeId)
                        .orElseThrow();

        assertEquals(user.getUserId(), stored.getUserId());
        assertEquals(0, stored.getFailedAttempts());
        assertTrue(stored.isUsable(now));

        stored.registerFailedAttempt();

        challengeRepository.saveAndFlush(stored);

        entityManager.clear();

        TwoFactorChallengeEntity updated =
                challengeRepository.findById(challengeId)
                        .orElseThrow();

        assertEquals(1, updated.getFailedAttempts());
        assertNull(updated.getConsumedAt());
    }
}
