
package com.smart.drop.shared.infrastructure.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import com.smart.drop.iam.application.services.AuthService;
import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.repositories.UserRepository;
import com.smart.drop.iam.interfaces.rest.AuthController;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigHttpTest {


@Autowired
private MockMvc mockMvc;

@MockitoBean
private AuthService authService;

@MockitoBean
private JwtService jwtService;

@MockitoBean
private UserRepository userRepository;

// Avoid loading real JPA metadata in this MVC slice test.
@MockitoBean(name = "jpaMappingContext")
private JpaMetamodelMappingContext jpaMappingContext;


    private static final String[] AUTH_PATHS = {
        "/api/v1/iam/auth",
        "/api/iam/auth",
        "/api/identity/auth"
    };


@Test
void validJwtMustNotBypassDeniedEndpoints()
        throws Exception {

    User user = User.createFromDatabase(
            42,
            "Security Test",
            "test@example.com",
            "hashed-password",
            new HashSet<>(),
            LocalDateTime.now(),
            null,
            false,
            null
    );

    // Simulate a successfully verified JWT.
    when(jwtService.verify("valid-test-token"))
            .thenReturn(
                    Jwts.claims()
                            .subject("42")
                            .build()
            );

    when(userRepository.findById(42))
            .thenReturn(Optional.of(user));

    for (String path : AUTH_PATHS) {

        mockMvc.perform(
                get(path + "/users")
                        .header(
                                "Authorization",
                                "Bearer valid-test-token"
                        )
        )
        .andExpect(status().isForbidden());
    }

    verify(userRepository, atLeastOnce())
            .findById(42);

    verify(authService, never()).getAllUsers();
}

@Test
void invalidJwtMustNotGrantAccess()
        throws Exception {

    // Simulate a token rejected during validation.
    when(jwtService.verify("invalid-test-token"))
            .thenThrow(
                    new JwtException("Invalid JWT")
            );

    for (String path : AUTH_PATHS) {

        mockMvc.perform(
                get(path + "/users")
                        .header(
                                "Authorization",
                                "Bearer invalid-test-token"
                        )
        )
        .andExpect(status().isUnauthorized());
    }

    verify(authService, never()).getAllUsers();
}


    @Test
    void loginMustBePublicOnAllAliases() throws Exception {

        User user = User.createFromDatabase(
            42,
            "Test User",
            "test@example.com",
            "hashed-password",
            new HashSet<>(),
            LocalDateTime.now(),
            null,
            false,
            null
        );

        when(authService.loginUser(
            "test@example.com",
            "Password123!"
        )).thenReturn(user);

        when(jwtService.createAccessToken(
            42,
            "test@example.com"
        )).thenReturn("test-access-token");

        for (String path : AUTH_PATHS) {
            mockMvc.perform(
                post(path + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "test@example.com",
                          "password": "Password123!"
                        }
                        """)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.accessToken")
                    .value("test-access-token")
            );
        }
    }

    @Test
    void twoFactorVerificationMustBePublic()
            throws Exception {

        // The 2FA service is not configured in this
        // MVC test, so the controller must fail closed.
        for (String path : AUTH_PATHS) {

            mockMvc.perform(
                post(path + "/2fa/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "challengeId": "test-challenge",
                          "code": "123456"
                        }
                        """)
            )
            .andExpect(
                status().isServiceUnavailable()
            );
        }
    }

    @Test
    void roleManagementMustRequireAuthorization()
            throws Exception {

        for (String path : AUTH_PATHS) {

            mockMvc.perform(
                post(path + "/assign-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "userId": 42,
                          "roleName": "ADMIN"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized());

            mockMvc.perform(
                post(path + "/revoke-role")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "userId": 42,
                          "roleName": "ADMIN"
                        }
                        """)
            )
            .andExpect(status().isUnauthorized());
        }

        verify(authService, never())
            .assignRoleToUser(anyInt(), anyString());

        verify(authService, never())
            .revokeRoleFromUser(anyInt(), anyString());
    }

    @Test
    void sensitiveEndpointsMustRemainClosed()
            throws Exception {

        for (String path : AUTH_PATHS) {

            mockMvc.perform(
                get(path + "/users")
            )
            .andExpect(status().isUnauthorized());

            mockMvc.perform(
                post(path + "/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnauthorized());

            mockMvc.perform(
                post(path + "/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isUnauthorized());
        }
    }
}
