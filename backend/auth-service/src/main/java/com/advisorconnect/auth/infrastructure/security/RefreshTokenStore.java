package com.advisorconnect.auth.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh_token:";

    private final StringRedisTemplate redis;

    @Value("${jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    public void store(String token, UUID userId) {
        redis.opsForValue().set(
                KEY_PREFIX + token,
                userId.toString(),
                Duration.ofMillis(refreshTokenExpiryMs)
        );
    }

    public Optional<UUID> validate(String token) {
        String value = redis.opsForValue().get(KEY_PREFIX + token);
        return Optional.ofNullable(value).map(UUID::fromString);
    }

    public void revoke(String token) {
        redis.delete(KEY_PREFIX + token);
    }
}
