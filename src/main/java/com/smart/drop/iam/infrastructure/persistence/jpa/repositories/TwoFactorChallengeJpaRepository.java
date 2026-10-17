
package com.smart.drop.iam.infrastructure.persistence.jpa.repositories;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TwoFactorChallengeJpaRepository
        extends JpaRepository<TwoFactorChallengeEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM TwoFactorChallengeEntity c
        WHERE c.challengeId = :challengeId
    """)
    Optional<TwoFactorChallengeEntity> findByIdForUpdate(
            @Param("challengeId") String challengeId
    );

    @Query("""
        SELECT CASE WHEN COUNT(c) > 0
                    THEN true ELSE false END
        FROM TwoFactorChallengeEntity c
        WHERE c.userId = :userId
          AND c.consumedAt IS NULL
          AND c.expiresAt > :now
          AND c.failedAttempts < :maxAttempts
    """)
    boolean existsActiveForUser(
            @Param("userId") Integer userId,
            @Param("now") LocalDateTime now,
            @Param("maxAttempts") int maxAttempts
    );

    Optional<TwoFactorChallengeEntity>
        findTopByUserIdOrderByCreatedAtDesc(Integer userId);
}
