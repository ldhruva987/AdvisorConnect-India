package com.advisorconnect.user.config;

import com.advisorconnect.common.security.HeaderAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The API Gateway validates the JWT and injects X-User-Id / X-User-Role; this service
 * re-derives identity from those headers via {@link HeaderAuthenticationFilter} and enforces
 * its own authorisation rules on top.
 *
 * <p>Previously every route was {@code permitAll()} on the assumption that the gateway was the
 * only way in — that is zero defence-in-depth: anything that can reach the service port
 * directly (a misconfigured ingress, a compromised pod on the same network) had unrestricted
 * access to every profile. Only genuinely public reads are permitted now.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()

                        // MUST precede the public "/users/{id}" rule below — "me" is a single
                        // path segment and would otherwise match it, leaving the caller's own
                        // profile route unauthenticated. First match wins, so claim it first.
                        .requestMatchers("/users/me").authenticated()

                        // Public profile lookup by id: UserController#getProfile carries no auth
                        // annotation and returns the same public profile the advisor directory
                        // already exposes, so this stays open by design.
                        .requestMatchers(HttpMethod.GET, "/users/{id}").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
