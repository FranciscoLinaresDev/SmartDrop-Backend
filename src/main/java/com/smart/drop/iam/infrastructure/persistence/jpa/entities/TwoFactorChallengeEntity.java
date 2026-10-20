
package com.smart.drop.iam.infrastructure.persistence.jpa.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "TwoFactorChallenges",
    indexes = {
        @Index(
            name = "IX_TwoFactorChallenges_UserId",
            columnList = "UserId"
        ),
        @Index(
            name = "IX_TwoFactorChallenges_ExpiresAt",
            columnList = "ExpiresAt"
        )
    }
)
public class TwoFactorChallengeEntity {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @Column(name = "ChallengeId", length = 36, nullable = false)
    private String challengeId;

    @Column(name = "UserId", nullable = false)
    private Integer userId;

    @Column(name = "CodeHash", length = 64, nullable = false)
    private String codeHash;

    @Column(name = "CreatedAt", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "ExpiresAt", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "FailedAttempts", nullable = false)
    private int failedAttempts;

    @Column(name = "ConsumedAt")
    private LocalDateTime consumedAt;

    @Version
    @Column(name = "RowVersion", nullable = false)
    private long rowVersion;

    protected TwoFactorChallengeEntity() {
    }

    public static TwoFactorChallengeEntity create(
            Integer userId,
            String codeHash,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        if (codeHash == null || codeHash.length() != 64) {
            throw new IllegalArgumentException("Invalid code hash");
        }

        if (createdAt == null || expiresAt == null ||
                !expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "Invalid challenge expiration"
            );
        }

        TwoFactorChallengeEntity challenge =
                new TwoFactorChallengeEntity();

        challenge.challengeId = UUID.randomUUID().toString();
        challenge.userId = userId;
        challenge.codeHash = codeHash;
        challenge.createdAt = createdAt;
        challenge.expiresAt = expiresAt;
        challenge.failedAttempts = 0;

        return challenge;
    }

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null
                && now.isBefore(expiresAt)
                && failedAttempts < MAX_ATTEMPTS;
    }

    public void registerFailedAttempt() {
        failedAttempts++;
    }

    public void consume(LocalDateTime now) {
        if (!isUsable(now)) {
            throw new IllegalStateException(
                    "Challenge is no longer valid"
            );
        }

        consumedAt = now;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }
}
