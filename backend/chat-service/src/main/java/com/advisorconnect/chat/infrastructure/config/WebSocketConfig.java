package com.advisorconnect.chat.infrastructure.config;

import com.advisorconnect.chat.adapter.in.websocket.ChatWebSocketHandler;
import com.advisorconnect.chat.infrastructure.websocket.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

/**
 * Registers the chat socket.
 *
 * <p>{@code setAllowedOriginPatterns("*")} is safe only because
 * {@link JwtHandshakeInterceptor} authenticates every handshake: the socket carries no
 * ambient credential (no cookie, no session) that a foreign origin could ride on, and an
 * unauthenticated handshake is refused before the upgrade.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
