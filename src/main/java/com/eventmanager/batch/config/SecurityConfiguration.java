package com.eventmanager.batch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Replaces Spring Boot's default security (generated {@code user} password + form login) which
 * rejects unauthenticated POSTs to the batch REST API with 401.
 * <p>
 * Job trigger endpoints are intended for server-to-server calls from the main backend on a
 * trusted network. Protect at the network / gateway layer in production, or add HTTP Basic or JWT
 * here if exposed more broadly.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        "/api/batch-jobs/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus",
                        "/actuator/metrics"
                    )
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .authenticated()
                    .anyRequest()
                    .denyAll()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
}
