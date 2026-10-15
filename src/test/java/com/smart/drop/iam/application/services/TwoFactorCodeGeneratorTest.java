
package com.smart.drop.iam.application.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorCodeGeneratorTest {

    private final TwoFactorCodeGenerator generator =
            new TwoFactorCodeGenerator();

    @Test
    void shouldGenerateSixDigitCodes() {
        for (int i = 0; i < 500; i++) {
            String code = generator.generateCode();

            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(code.matches("[0-9]{6}"));
        }
    }

    @Test
    void shouldGenerateDifferentCodes() {
        String first = generator.generateCode();

        boolean foundDifferent = false;

        for (int i = 0; i < 100; i++) {
            if (!first.equals(generator.generateCode())) {
                foundDifferent = true;
                break;
            }
        }

        assertTrue(foundDifferent);
    }
}
