
package com.smart.drop.iam.infrastructure.configuration;

import com.smart.drop.iam.application.services.*;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TwoFactorConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            TwoFactorConfiguration.class
                    );

    @Test
    void shouldKeepTwoFactorDisabledByDefault() {

        runner.run(context -> {
            assertNull(context.getStartupFailure());

            assertFalse(context.containsBean(
                    "twoFactorChallengeService"
            ));

            assertFalse(context.containsBean(
                    "twoFactorCodeSender"
            ));
        });
    }

    @Test
    void shouldCreateBeansWhenTwoFactorIsEnabled() {

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String secret =
                Base64.getEncoder().encodeToString(key);

        runner
                .withPropertyValues(
                        "smartdrop.two-factor.enabled=true",
                        "SMARTDROP_TWO_FACTOR_HMAC_KEY=" + secret,
                        "SMARTDROP_MAIL_FROM=noreply@example.com"
                )
                .withBean(
                        TwoFactorChallengeJpaRepository.class,
                        () -> mock(
                                TwoFactorChallengeJpaRepository.class
                        )
                )
                .withBean(
                        TwoFactorCodeGenerator.class,
                        TwoFactorCodeGenerator::new
                )
                .withBean(
                        JavaMailSender.class,
                        () -> mock(JavaMailSender.class)
                )
                .run(context -> {

                    assertNull(context.getStartupFailure());

                    assertNotNull(context.getBean(
                            TwoFactorCodeHasher.class
                    ));

                    assertNotNull(context.getBean(
                            TwoFactorCodeSender.class
                    ));

                    assertNotNull(context.getBean(
                            TwoFactorChallengeService.class
                    ));
                });
    }
}
