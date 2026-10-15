
package com.smart.drop.shared.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService createService() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String secret = Base64.getEncoder().encodeToString(key);

        return new JwtService(secret);
    }

    @Test
    void shouldCreateAndVerifyValidToken() {
        JwtService jwtService = createService();

        String token = jwtService.createAccessToken(
                42,
                "test@example.com"
        );

        Claims claims = jwtService.verify(token);

        assertEquals("42", claims.getSubject());
        assertEquals("smartdrop", claims.getIssuer());
        assertEquals(
                "test@example.com",
                claims.get("email", String.class)
        );

        assertNotNull(claims.getExpiration());
    }

    @Test
    void shouldRejectTokenWithInvalidSignature() {
        JwtService issuer = createService();
        JwtService verifier = createService();

        String token = issuer.createAccessToken(
                42,
                "test@example.com"
        );

        assertThrows(
                JwtException.class,
                () -> verifier.verify(token)
        );
    }

    @Test
    void shouldRejectMalformedToken() {
        JwtService jwtService = createService();

        assertThrows(
                JwtException.class,
                () -> jwtService.verify("invalid.token.value")
        );
    }
}
