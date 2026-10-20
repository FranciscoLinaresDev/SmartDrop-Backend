
package com.smart.drop.iam.application.services;

import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.repositories.AdminRoleGuard;
import com.smart.drop.iam.domain.model.repositories.UserRepository;
import com.smart.drop.iam.domain.model.repositories.RoleRepository;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServicePasswordSecurityTest {

    private final UserRepository userRepository =
            mock(UserRepository.class);

    private final RoleRepository roleRepository =
            mock(RoleRepository.class);

    private final AdminRoleGuard adminRoleGuard =
            mock(AdminRoleGuard.class);

    private final PasswordEncoder encoder =
            new BCryptPasswordEncoder(12);

    private final AuthService authService =
            new AuthService(
                    userRepository,
                    roleRepository,
                    encoder,
                    adminRoleGuard
            );

    @Test
    void registerShouldHashPassword() {

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        User user = authService.registerUser(
                "Test User",
                "test@example.com",
                "Password123!"
        );

        assertNotEquals(
                "Password123!",
                user.getPasswordHash()
        );

        assertTrue(
                encoder.matches(
                        "Password123!",
                        user.getPasswordHash()
                )
        );
    }

    @Test
    void loginShouldAcceptCorrectPassword() {

        User user = createExistingUser("Password123!");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        User result = authService.loginUser(
                "test@example.com",
                "Password123!"
        );

        assertSame(user, result);
    }

    @Test
    void loginShouldRejectIncorrectPassword() {

        User user = createExistingUser("Password123!");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        assertThrows(
                IllegalArgumentException.class,
                () -> authService.loginUser(
                        "test@example.com",
                        "WrongPassword"
                )
        );
    }

    @Test
    void changePasswordShouldStoreHash() {

        User user = createExistingUser("OldPassword123!");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));

        authService.changePassword(
                "test@example.com",
                "OldPassword123!",
                "NewPassword123!"
        );

        assertNotEquals(
                "NewPassword123!",
                user.getPasswordHash()
        );

        assertTrue(
                encoder.matches(
                        "NewPassword123!",
                        user.getPasswordHash()
                )
        );

        verify(userRepository).update(user);
    }

    private User createExistingUser(String password) {

        return User.createFromDatabase(
                1,
                "Test User",
                "test@example.com",
                encoder.encode(password),
                new HashSet<>(),
                LocalDateTime.now(),
                null,
                false,
                null
        );
    }
}
