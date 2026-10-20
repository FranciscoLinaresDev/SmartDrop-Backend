
package com.smart.drop.iam.infrastructure.persistence.jpa.repositories;

import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserJpaRepository
        extends JpaRepository<UserEntity, Integer> {

    Optional<UserEntity> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM UserEntity u
        WHERE u.userId = :userId
    """)
    Optional<UserEntity> findByIdForUpdate(
            @Param("userId") Integer userId
    );
}
