package com.advisorconnect.chat.infrastructure.config;

import com.advisorconnect.common.security.HeaderAuthenticationFilter;
import com.advisorconnect.chat.infrastructure.websocket.JwtHandshakeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authorisation for chat-service's <strong>REST</strong> surface.
 *
 * <p>The gateway validates the JWT and injects {@code X-User-Id}/{@code X-User-Role};
 * {@link HeaderAuthenticationFilter} turns those back into an {@code Authentication} so
 * {@code .authenticated()} and {@code @PreAuthorize} have something real to work with. Without this
 * config the whole {@code /chats/**} surface would be wide open to anything that can reach the
 * service port directly — chat history is private by definition, so that is not an acceptable
 * default even behind a gateway.
 *
 * <h2>Why {@code /ws/**} is permitted here</h2>
 * The WebSocket endpoint is <em>not</em> unauthenticated — it is authenticated by a different
 * mechanism, and this rule is what lets that mechanism run at all.
 *
 * <p>A browser's {@code WebSocket} constructor cannot set an {@code Authorization} header, so the
 * handshake carries its credential as {@code ?token=} and is verified by
 * {@link JwtHandshakeInterceptor}, which rejects a bad or absent token with 401 before the
 * connection is ever upgraded. The handshake also arrives with no {@code X-User-Id} header at all,
 * so {@code HeaderAuthenticationFilter} can never authenticate it.
 *
 * <p>Letting {@code .anyRequest().authenticated()} cover {@code /ws/**} would therefore reject
 * every handshake with 401 <em>before</em> the interceptor could examine the token — the socket
 * would be unreachable for everyone, including legitimate clients, and the failure would look like
 * a token problem rather than a filter-chain ordering problem. The endpoint is excluded from this
 * chain precisely so its own, stricter, cryptographic check is the one that runs.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Explicit {@code @Bean} because {@code HeaderAuthenticationFilter} lives in
     * {@code com.advisorconnect.common}, which this service's component scan never reaches.
     */
    @Bean
    public HeaderAuthenticationFilter headerAuthenticationFilter() {
        return new HeaderAuthenticationFilter();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // No cookies or sessions are involved — the identity is a per-request header — so
                // there is no ambient credential for CSRF to protect.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Authenticated by JwtHandshakeInterceptor instead — see the class javadoc.
                        .requestMatchers("/ws/**").permitAll()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
