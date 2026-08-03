package com.advisorconnect.booking.infrastructure.config;

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
 * Booking-service had no {@code SecurityConfig} at all, which meant the
 * {@code @PreAuthorize("isAuthenticated()")} annotations on {@code BookingController}
 * had nothing backing them.
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

                        // Public availability check — a visitor must be able to see open slots
                        // before deciding to sign up.
                        .requestMatchers(HttpMethod.GET, "/bookings/availability/{advisorId}").permitAll()

                        // Razorpay calls this server-to-server and will never present a JWT or the
                        // gateway's X-User-* headers, so it cannot be an authenticated route.
                        // It is not unauthenticated in practice: RazorpayWebhookController rejects
                        // anything whose X-Razorpay-Signature HMAC does not verify against the
                        // endpoint's webhook secret, and does nothing before that check passes.
                        .requestMatchers(HttpMethod.POST, "/bookings/webhooks/razorpay").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
