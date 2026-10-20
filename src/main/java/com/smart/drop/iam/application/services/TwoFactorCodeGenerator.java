
package com.smart.drop.iam.application.services;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class TwoFactorCodeGenerator {

    private static final int CODE_BOUND = 1_000_000;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateCode() {
        int number = secureRandom.nextInt(CODE_BOUND);

        return String.format(Locale.ROOT, "%06d", number);
    }
}
