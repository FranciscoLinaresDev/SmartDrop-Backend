
package com.smart.drop.iam.infrastructure.persistence.jpa.guards;

import com.smart.drop.iam.domain.model.repositories.AdminRoleGuard;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.RoleEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAdminRoleGuard implements AdminRoleGuard {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanRemoveAdminRole(Integer adminRoleId) {

        if (adminRoleId == null || adminRoleId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid administrator role"
            );
        }

        // All ADMIN revocations must lock this same row.
        RoleEntity adminRole = entityManager.find(
                RoleEntity.class,
                adminRoleId,
                LockModeType.PESSIMISTIC_WRITE
        );

        if (adminRole == null) {
            throw new IllegalStateException(
                    "Administrator role not found"
            );
        }

        // Lock the current administrators before counting.
        // This also avoids relying only on a potentially
        // stale snapshot under repeatable-read isolation.
        var administrators = entityManager.createQuery(
                """
                SELECT u
                FROM UserEntity u
                JOIN u.roles r
                WHERE r = :adminRole
                """,
                UserEntity.class
        )
        .setParameter("adminRole", adminRole)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();

        if (administrators.size() <= 1) {
            throw new IllegalStateException(
                    "Cannot remove the last system administrator"
            );
        }
    }
}
