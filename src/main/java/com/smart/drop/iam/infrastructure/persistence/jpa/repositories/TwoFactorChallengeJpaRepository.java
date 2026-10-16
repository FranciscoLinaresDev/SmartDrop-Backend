
package com.smart.drop.iam.infrastructure.persistence.jpa.repositories;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.TwoFactorChallengeEntity;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
