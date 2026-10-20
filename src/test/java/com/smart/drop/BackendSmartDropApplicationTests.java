package com.smart.drop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.security.SecureRandom;
import java.util.Base64;

@SpringBootTest
class BackendSmartDropApplicationTests {

@DynamicPropertySource
static void configureJwtForTests(DynamicPropertyRegistry registry) {

    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);

    String testSecret = Base64.getEncoder().encodeToString(key);

    registry.add(
            "authorization.jwt.secret",
            () -> testSecret
    );

    registry.add(
            "identity.token.jwt-secret",
            () -> testSecret
    );
}


    @Test
    void contextLoads() {
    }


}
