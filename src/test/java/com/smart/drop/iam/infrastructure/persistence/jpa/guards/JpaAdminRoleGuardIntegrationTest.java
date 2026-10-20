
package com.smart.drop.iam.infrastructure.persistence.jpa.guards;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.smart.drop.iam.application.services.AuthService;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.RoleEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.entities.UserEntity;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.RoleJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.profiles.active=dev",
        "spring.datasource.url=jdbc:h2:mem:smartdrop_admin_guard_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "smartdrop.demo.seed.enabled=false",
        "smartdrop.two-factor.enabled=false"
})
class JpaAdminRoleGuardIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserJpaRepository users;

    @Autowired
    private RoleJpaRepository roles;

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {

        registry.add(
                "authorization.jwt.secret",
                JpaAdminRoleGuardIntegrationTest::randomSecret
        );
    }

    private static String randomSecret() {

        byte[] bytes = new byte[32];

        new SecureRandom().nextBytes(bytes);

        return Base64.getEncoder()
                .encodeToString(bytes);
    }

    @BeforeEach
    void cleanDatabase() {

        users.deleteAll();
        roles.deleteAll();
    }

    @Test
    void shouldPreventRemovingLastAdministrator() {

        RoleEntity adminRole = createAdminRole();

        UserEntity onlyAdmin = createAdmin(
                "only-admin@example.com",
                adminRole
        );

        Integer adminId = onlyAdmin.getUserId();

        assertThrows(
                IllegalStateException.class,
                () -> authService.revokeRoleFromUser(
                        adminId,
                        "ROLE_ADMIN"
                )
        );

        // The administrator must retain the role.
        UserEntity storedAdmin = users.findById(adminId)
                .orElseThrow();

        assertEquals(
                1,
                storedAdmin.getRoles().size()
        );
    }

    @Test
    void shouldAllowRevocationWhenAnotherAdminExists() {

        RoleEntity adminRole = createAdminRole();

        UserEntity firstAdmin = createAdmin(
                "first-admin@example.com",
                adminRole
        );

        UserEntity secondAdmin = createAdmin(
                "second-admin@example.com",
                adminRole
        );

        Integer firstId = firstAdmin.getUserId();
        Integer secondId = secondAdmin.getUserId();

        authService.revokeRoleFromUser(
                firstId,
                "ROLE_ADMIN"
        );

        UserEntity updatedFirst = users.findById(firstId)
                .orElseThrow();

        UserEntity storedSecond = users.findById(secondId)
                .orElseThrow();

        // The first administrator lost the role.
        assertTrue(
                updatedFirst.getRoles().isEmpty()
        );

        // The other administrator still has ROLE_ADMIN.
        assertEquals(
                1,
                storedSecond.getRoles().size()
        );
    }

    private RoleEntity createAdminRole() {

        return roles.saveAndFlush(
                new RoleEntity(
                        "ROLE_ADMIN",
                        "Administrador Global"
                )
        );
    }

    private UserEntity createAdmin(
            String email,
            RoleEntity adminRole) {

        UserEntity user = new UserEntity(
                "Test Administrator",
                email,
                "test-password-hash"
        );

        user.getRoles().add(adminRole);

        return users.saveAndFlush(user);
    }

@Test
void concurrentRevocationsMustPreserveOneAdministrator()
        throws Exception {

    RoleEntity adminRole = createAdminRole();

    Integer firstId = createAdmin(
            "concurrent-admin-1@example.com",
            adminRole
    ).getUserId();

    Integer secondId = createAdmin(
            "concurrent-admin-2@example.com",
            adminRole
    ).getUserId();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    ExecutorService executor =
            Executors.newFixedThreadPool(2);

    Callable<Boolean> firstRequest =
            () -> attemptAdminRevocation(
                    firstId, ready, start
            );

    Callable<Boolean> secondRequest =
            () -> attemptAdminRevocation(
                    secondId, ready, start
            );

    try {
        Future<Boolean> first =
                executor.submit(firstRequest);

        Future<Boolean> second =
                executor.submit(secondRequest);

        assertTrue(
                ready.await(15, TimeUnit.SECONDS),
                "Both requests must be ready"
        );

        start.countDown();

        boolean firstSucceeded =
                first.get(30, TimeUnit.SECONDS);

        boolean secondSucceeded =
                second.get(30, TimeUnit.SECONDS);

        int successfulRevocations =
                (firstSucceeded ? 1 : 0)
                + (secondSucceeded ? 1 : 0);

        assertEquals(
                1,
                successfulRevocations,
                "Exactly one administrator may lose the role"
        );

    } finally {
        start.countDown();
        executor.shutdownNow();
    }

    long remainingAdministrators = users.findAll()
            .stream()
            .filter(user -> user.getRoles()
                    .stream()
                    .anyMatch(role ->
                            "ROLE_ADMIN".equals(role.getName())
                    )
            )
            .count();

    assertEquals(
            1,
            remainingAdministrators,
            "The system must retain one administrator"
    );
}

private boolean attemptAdminRevocation(
        Integer userId,
        CountDownLatch ready,
        CountDownLatch start) throws InterruptedException {

    ready.countDown();

    if (!start.await(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
                "Concurrent test start timed out"
        );
    }

    try {
        authService.revokeRoleFromUser(
                userId,
                "ROLE_ADMIN"
        );

        return true;

    } catch (IllegalStateException exception) {

        if ("Cannot remove the last system administrator"
                .equals(exception.getMessage())) {
            return false;
        }

        throw exception;
    }
}

}
