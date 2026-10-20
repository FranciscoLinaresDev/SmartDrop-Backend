
package com.smart.drop.shared.infrastructure.security;

import com.smart.drop.iam.domain.model.User;
import com.smart.drop.iam.domain.model.repositories.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        jwtService = new JwtService(
                Base64.getEncoder().encodeToString(key)
        );

        userRepository = mock(UserRepository.class);

        filter = new JwtAuthenticationFilter(
                jwtService,
                userRepository
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateValidToken() throws Exception {
        User user = createUser();

        when(userRepository.findById(42))
                .thenReturn(Optional.of(user));

        MockHttpServletRequest request = createRequest();

        request.addHeader(
                "Authorization",
                "Bearer " + jwtService.createAccessToken(
                        42, "test@example.com")
        );

        executeFilter(request);

        var authentication =
                SecurityContextHolder.getContext().getAuthentication();

        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertEquals("42", authentication.getName());
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        MockHttpServletRequest request = createRequest();

        request.addHeader(
                "Authorization",
                "Bearer invalid.token.value"
        );

        executeFilter(request);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectTokenForNonexistentUser() throws Exception {
        when(userRepository.findById(42))
                .thenReturn(Optional.empty());

        MockHttpServletRequest request = createRequest();

        request.addHeader(
                "Authorization",
                "Bearer " + jwtService.createAccessToken(
                        42, "test@example.com")
        );

        executeFilter(request);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );
    }

    @Test
    void shouldNotAuthenticateWithoutToken() throws Exception {
        executeFilter(createRequest());

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verifyNoInteractions(userRepository);
    }

    private MockHttpServletRequest createRequest() {
        return new MockHttpServletRequest(
                "GET",
                "/api/v1/inventory/inventories"
        );
    }

    private void executeFilter(MockHttpServletRequest request)
            throws Exception {

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );
    }

    private User createUser() {
        return User.createFromDatabase(
                42,
                "Test User",
                "test@example.com",
                "test-password-hash",
                new HashSet<>(),
                LocalDateTime.now(),
                null,
                false,
                null
        );
    }
}
