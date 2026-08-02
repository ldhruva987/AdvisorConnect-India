package com.advisorconnect.advisor.infrastructure.config;

import com.advisorconnect.common.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Advisor-service had no {@code SecurityConfig} at all, which meant the
 * {@code @PreAuthorize} annotations on {@code AdvisorController} had nothing backing them.
 * This wires the gateway-header identity filter and enables method security so those
 * annotations actually enforce.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()

                        // MUST precede the public "/advisors/{username}" rule below: the
                        // single-segment path variable would otherwise happily match the literal
                        // segment "applications", making the admin application list (added in a
                        // later phase) publicly readable. First match wins, so claim it first.
                        .requestMatchers("/advisors/applications", "/advisors/applications/**")
                            .authenticated()

                        // Public browse surface. Deliberately NOT "/advisors/**" — a broad
                        // wildcard here would expose every current and future sub-resource.
                        .requestMatchers(HttpMethod.GET, "/advisors").permitAll()
                        .requestMatchers(HttpMethod.GET, "/advisors/{username}").permitAll()

                        // Reviews are part of the public profile, so reading them is public —
                        // but only reading them. Scoped to GET on purpose: POST to this exact
                        // same path submits a review and must stay authenticated, since the
                        // reviewer's identity comes from the gateway headers. Pinned to the
                        // literal trailing "reviews" segment rather than a "/advisors/*/**"
                        // wildcard, which would also swallow every future sub-resource.
                        .requestMatchers(HttpMethod.GET, "/advisors/{id}/reviews").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
