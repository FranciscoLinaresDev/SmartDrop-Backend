
package com.smart.drop.iam.application.services;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

public class TwoFactorChallengeService {

    private final UserJpaRepository userRepository;
    private final TwoFactorChallengeJpaRepository repository;
    private final TwoFactorCodeGenerator generator;
    private final TwoFactorCodeHasher hasher;
    private final TwoFactorCodeSender sender;
    private final Clock clock;



public TwoFactorChallengeService(
        TwoFactorChallengeJpaRepository repository,
        UserJpaRepository userRepository,
        TwoFactorCodeGenerator generator,
        TwoFactorCodeHasher hasher,
        TwoFactorCodeSender sender,
        Clock clock) {

    this.repository = repository;
    this.userRepository = userRepository;
    this.generator = generator;
    this.hasher = hasher;
    this.sender = sender;
    this.clock = clock;
}


@Transactional
public String createAfterPasswordVerified(
        Integer userId,
        String email) {

    if (userId == null || userId <= 0 ||
            email == null || email.isBlank()) {
        throw new IllegalArgumentException(
                "Invalid challenge request"
        );
    }

    // Lock user row to serialize challenge creation.
    UserEntity user = userRepository
            .findByIdForUpdate(userId)
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Invalid user"
                    )
            );

    // Never send a code to an arbitrary email address.
    if (!user.getEmail().equalsIgnoreCase(email)) {
        throw new IllegalArgumentException(
                "Invalid challenge request"
        );
    }

    LocalDateTime now = LocalDateTime.now(clock);

    // Only one active challenge per user.
    if (repository.existsActiveForUser(
            userId, now, 5)) {

        throw new IllegalStateException(
                "An active verification challenge already exists"
        );
    }

    // Require a 60-second cooldown between requests.
    var latest = repository
            .findTopByUserIdOrderByCreatedAtDesc(userId);

    if (latest.isPresent() &&
            latest.get().getCreatedAt()
                    .isAfter(now.minusSeconds(60))) {

        throw new IllegalStateException(
                "Please wait before requesting another code"
        );
    }

    String code = generator.generateCode();

    TwoFactorChallengeEntity challenge =
            TwoFactorChallengeEntity.create(
                    userId,
                    hasher.hash(userId, code),
                    now,
                    now.plusMinutes(5)
            );

    repository.save(challenge);

    sender.sendCode(user.getEmail(), code);

    return challenge.getChallengeId();
}

    @Transactional
    public VerificationResult verify(
            String challengeId,
            String code) {

        if (challengeId == null || challengeId.isBlank()) {
            return VerificationResult.invalid();
        }

        var optionalChallenge =
                repository.findByIdForUpdate(challengeId);

        if (optionalChallenge.isEmpty()) {
            return VerificationResult.invalid();
        }

        var challenge = optionalChallenge.get();
        LocalDateTime now = LocalDateTime.now(clock);

        if (!challenge.isUsable(now)) {
            return VerificationResult.invalid();
        }

        if (!hasher.matches(
                challenge.getUserId(),
                code,
                challenge.getCodeHash())) {

            challenge.registerFailedAttempt();
            repository.save(challenge);

            // Return normally so failed attempts can be committed.
            return VerificationResult.invalid();
        }

        challenge.consume(now);
        repository.save(challenge);

        return new VerificationResult(
                true,
                challenge.getUserId()
        );
    }

    public record VerificationResult(
            boolean verified,
            Integer userId) {

        public static VerificationResult invalid() {
            return new VerificationResult(false, null);
        }
    }
}
