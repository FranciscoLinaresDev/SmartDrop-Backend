
package com.smart.drop.iam.application.services;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

public class TwoFactorCodeHasher {

    private final SecretKeySpec key;

    public TwoFactorCodeHasher(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("2FA secret is required");
        }

        byte[] decoded = Base64.getDecoder().decode(secretBase64);

        if (decoded.length < 32) {
            throw new IllegalArgumentException(
                    "2FA secret must contain at least 256 bits"
            );
        }

        this.key = new SecretKeySpec(decoded, "HmacSHA256");
    }

    public String hash(Integer userId, String code) {
        if (userId == null || userId <= 0 ||
                code == null || !code.matches("[0-9]{6}")) {
            throw new IllegalArgumentException("Invalid 2FA input");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);

            String payload = userId + ":" + code;

            byte[] digest = mac.doFinal(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(digest);

        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Unable to hash 2FA code", ex
            );
        }
    }

    public boolean matches(
            Integer userId,
            String code,
            String expectedHash) {

        if (code == null || !code.matches("[0-9]{6}") ||
                expectedHash == null || expectedHash.length() != 64) {
            return false;
        }

        String actualHash = hash(userId, code);

        return MessageDigest.isEqual(
                actualHash.getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII)
        );
    }
}
