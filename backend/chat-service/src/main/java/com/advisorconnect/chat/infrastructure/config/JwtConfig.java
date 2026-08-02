package com.advisorconnect.chat.infrastructure.config;

import com.advisorconnect.common.security.JwtValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link JwtValidator} from {@code common} into this service's context.
 *
 * <p>{@code JwtValidator} is deliberately not a {@code @Component}: this service's component
 * scan is rooted at {@code com.advisorconnect.chat} and never reaches
 * {@code com.advisorconnect.common}. Every service that needs it declares an explicit
 * {@code @Bean} exactly like this one.
 *
 * <p>The secret must be byte-identical to the one auth-service signs with
 * ({@code jwt.secret}, sourced from {@code JWT_SECRET}), or no token will verify here.
 */
@Configuration
public class JwtConfig {

    @Bean
    JwtValidator jwtValidator(@Value("${jwt.secret}") String secret) {
        return new JwtValidator(secret);
    }
}
