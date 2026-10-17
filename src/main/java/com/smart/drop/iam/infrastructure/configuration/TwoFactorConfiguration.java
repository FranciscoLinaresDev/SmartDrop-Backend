
package com.smart.drop.iam.infrastructure.configuration;

import com.smart.drop.iam.application.services.*;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.TwoFactorChallengeJpaRepository;
import com.smart.drop.iam.infrastructure.persistence.jpa.repositories.UserJpaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(
        prefix = "smartdrop.two-factor",
        name = "enabled",
        havingValue = "true"
)
public class TwoFactorConfiguration {

    @Bean
    public TwoFactorCodeHasher twoFactorCodeHasher(
            @Value("${SMARTDROP_TWO_FACTOR_HMAC_KEY}")
            String secret) {

        return new TwoFactorCodeHasher(secret);
    }

    @Bean
    public TwoFactorCodeSender twoFactorCodeSender(
            JavaMailSender mailSender,
            @Value("${SMARTDROP_MAIL_FROM}")
            String fromAddress) {

        return new TwoFactorEmailSender(
                mailSender,
                fromAddress
        );
    }

    @Bean
    public TwoFactorChallengeService twoFactorChallengeService(
            TwoFactorChallengeJpaRepository repository,
            UserJpaRepository userRepository,
            TwoFactorCodeGenerator generator,
            TwoFactorCodeHasher hasher,
            TwoFactorCodeSender sender) {

        return new TwoFactorChallengeService(
                repository,
                userRepository,
                generator,
                hasher,
                sender,
                Clock.systemUTC()
        );
    }

}
