
package com.smart.drop.shared.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class JwtService {

    private static final String ISSUER = "smartdrop";
    private static final Duration TOKEN_LIFETIME =
            Duration.ofMinutes(30);

    private final SecretKey signingKey;

    public JwtService(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "JWT signing secret is required"
            );
        }

        byte[] keyBytes = Decoders.BASE64.decode(secretBase64);

        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT signing key must contain at least 256 bits"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(
            Integer userId,
            String email) {

        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }

        Instant now = Instant.now();

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(userId.toString())
                .claim("email", email)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TOKEN_LIFETIME)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!ISSUER.equals(claims.getIssuer())) {
            throw new io.jsonwebtoken.JwtException(
                    "Invalid token issuer"
            );
        }

        return claims;
    }
}
