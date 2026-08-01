package com.advisorconnect.chat.adapter.in.websocket;

import com.advisorconnect.chat.application.ChatService;
import com.advisorconnect.chat.domain.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler — manages real-time chat connections.
 *
 * Pattern: Observer — broadcastToConversation notifies all session observers
 * subscribed to a given conversation key.
 *
 * Session storage: ConcurrentHashMap for thread-safe multi-user access.
 * Canonical conversation key (user:advisor sorted lexicographically) ensures
 * both participants share the same bucket regardless of join order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** conversationKey -> { sessionId -> WebSocketSession } */
    private final Map<String, Map<String, WebSocketSession>> conversationSessions = new ConcurrentHashMap<>();

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId    = extractUserId(session);
        String advisorId = extractAdvisorId(session);
        String key       = conversationKey(userId, advisorId);
        conversationSessions.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        log.info("WS connected: sessionId={} userId={} advisorId={}", session.getId(), userId, advisorId);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var payload = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) payload.get("type");

        if ("MESSAGE".equals(type)) {
            String text      = (String) payload.get("text");
            UUID userId      = UUID.fromString(extractUserId(session));
            UUID advisorId   = UUID.fromString(extractAdvisorId(session));
            Message saved    = chatService.sendMessage(userId, advisorId, text, "user");
            broadcastToConversation(userId.toString(), advisorId.toString(), saved);
        } else if ("TYPING".equals(type)) {
            broadcastToConversation(
                    extractUserId(session),
                    extractAdvisorId(session),
                    Map.of("type", "TYPING", "senderId", extractUserId(session))
            );
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        conversationSessions.values().forEach(sessions -> sessions.remove(session.getId()));
        log.info("WS disconnected: sessionId={} status={}", session.getId(), status);
    }

    public void broadcastToConversation(String userId, String advisorId, Object payload) {
        String key = conversationKey(userId, advisorId);
        var sessions = conversationSessions.getOrDefault(key, Map.of());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Failed to serialize broadcast payload: {}", e.getMessage());
            return;
        }
        sessions.values().forEach(s -> sendSafe(s, json));
    }

    private void sendSafe(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("Failed to send WS message to session {}: {}", session.getId(), e.getMessage());
        }
    }

    private static String extractUserId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        // In production extract from validated JWT passed as query param or header
        return extractParam(query, "userId");
    }

    private static String extractAdvisorId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        return extractParam(query, "advisorId");
    }

    private static String extractParam(String query, String param) {
        if (query == null) return "unknown";
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) return kv[1];
        }
        return "unknown";
    }

    /**
     * Canonical key: lexicographically smaller UUID first so that
     * user→advisor and advisor→user map to the same bucket.
     */
    private static String conversationKey(String userId, String advisorId) {
        return userId.compareTo(advisorId) < 0
                ? userId + ":" + advisorId
                : advisorId + ":" + userId;
    }
}
