
package com.smart.drop.shared.infrastructure.security;

import com.smart.drop.iam.domain.model.repositories.UserRepository;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            UserRepository userRepository) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Public authentication endpoints.
                .requestMatchers(
                    HttpMethod.POST,

                    "/api/v1/iam/auth/login",
                    "/api/iam/auth/login",
                    "/api/identity/auth/login",

                    "/api/v1/iam/auth/2fa/verify",
                    "/api/iam/auth/2fa/verify",
                    "/api/identity/auth/2fa/verify"
                ).permitAll()

                // Only administrators can list all users.
                .requestMatchers(
                    HttpMethod.GET,

                    "/api/v1/iam/auth/users",
                    "/api/iam/auth/users",
                    "/api/identity/auth/users"
                ).hasRole("ADMIN")

                // Only administrators can manage roles.
                .requestMatchers(
                    HttpMethod.POST,

                    "/api/v1/iam/auth/assign-role",
                    "/api/iam/auth/assign-role",
                    "/api/identity/auth/assign-role",

                    "/api/v1/iam/auth/revoke-role",
                    "/api/iam/auth/revoke-role",
                    "/api/identity/auth/revoke-role"
                ).hasRole("ADMIN")

                // Read an individual tank.
                // Ownership is checked by TankService.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/inventory/tanks/{tankId}"
                ).hasAnyRole("USER", "BREWER", "ADMIN")

                // Create a tank for the authenticated user.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/v1/inventory/tanks"
                ).hasAnyRole("USER", "BREWER", "ADMIN")

                // Update a tank owned by the authenticated user.
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/v1/inventory/tanks/{tankId}"
                ).hasAnyRole("USER", "BREWER", "ADMIN")

                // Replenish a tank owned by the authenticated user.
                .requestMatchers(
                    HttpMethod.PUT,
                    "/api/v1/inventory/tanks/{tankId}/replenish"
                ).hasAnyRole("USER", "BREWER", "ADMIN")
                // Delete only tanks owned by the authenticated user.
                .requestMatchers(
                    HttpMethod.DELETE,
                    "/api/v1/inventory/tanks/{tankId}"
                ).hasAnyRole("USER", "BREWER", "ADMIN")

                // Block all other routes by default.
                .anyRequest().denyAll()
            )

            .exceptionHandling(exceptions -> exceptions

                .authenticationEntryPoint(
                    (request, response, exception) ->
                        response.sendError(
                            HttpServletResponse.SC_UNAUTHORIZED
                        )
                )

                .accessDeniedHandler(
                    (request, response, exception) ->
                        response.sendError(
                            HttpServletResponse.SC_FORBIDDEN
                        )
                )
            )

            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())

            .addFilterBefore(
                new JwtAuthenticationFilter(
                    jwtService,
                    userRepository
                ),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public JwtService jwtService(
            @Value("${authorization.jwt.secret}")
            String secret) {

        return new JwtService(secret);
    }
}
