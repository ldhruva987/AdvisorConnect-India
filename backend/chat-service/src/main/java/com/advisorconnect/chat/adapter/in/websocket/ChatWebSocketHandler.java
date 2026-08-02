package com.advisorconnect.chat.adapter.in.websocket;

import com.advisorconnect.chat.adapter.in.web.dto.MessageResponse;
import com.advisorconnect.chat.adapter.in.websocket.dto.ChatSocketFrame;
import com.advisorconnect.chat.application.ChatService;
import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.infrastructure.websocket.JwtHandshakeInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler — manages real-time chat connections.
 *
 * <p>Pattern: Observer — broadcastToConversation notifies all session observers
 * subscribed to a given conversation key.
 *
 * <p>Session storage: ConcurrentHashMap for thread-safe multi-user access.
 * Canonical conversation key (user:advisor sorted lexicographically) ensures
 * both participants share the same bucket regardless of join order.
 *
 * <h2>Identity resolution</h2>
 * The caller's own id is read <em>only</em> from {@link WebSocketSession#getAttributes()}, which
 * {@link JwtHandshakeInterceptor} populates from a signature-verified JWT. It is never read from
 * the query string: previously both ids came off the query string, so any client could claim to
 * be any user simply by editing the URL.
 *
 * <p>The counterparty's id still comes from the query string, and legitimately so — the JWT
 * proves who is calling, not who they wish to reach. Which side of the conversation the verified
 * subject occupies is decided by its {@code role} claim:
 *
 * <ul>
 *   <li>{@code USER} → the subject is the {@code userId}; {@code ?advisorId=} names the counterparty.</li>
 *   <li>{@code ADVISOR} → the subject is the {@code advisorId}; {@code ?userId=} names the counterparty.</li>
 * </ul>
 *
 * <p>Any other role (e.g. {@code ADMIN}) is not a chat participant and is refused, as is a
 * malformed or absent counterparty id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler extends TextWebSocketHandler {

    static final String PARAM_USER_ID = "userId";
    static final String PARAM_ADVISOR_ID = "advisorId";

    /** conversationKey -> { sessionId -> WebSocketSession } */
    private final Map<String, Map<String, WebSocketSession>> conversationSessions = new ConcurrentHashMap<>();

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        ConversationParticipants participants = resolveParticipants(session);
        if (participants == null) {
            // The handshake was authenticated, but this socket cannot be attributed to a
            // conversation, so there is nothing it could safely be allowed to send.
            log.warn("WS rejected after handshake: sessionId={} could not be resolved to a conversation",
                    session.getId());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Unresolvable conversation participants"));
            return;
        }

        String key = conversationKey(participants.userId(), participants.advisorId());
        conversationSessions.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                .put(session.getId(), session);
        log.info("WS connected: sessionId={} userId={} advisorId={} as={}",
                session.getId(), participants.userId(), participants.advisorId(), participants.senderType());
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        ConversationParticipants participants = resolveParticipants(session);
        if (participants == null) {
            // Defensive: afterConnectionEstablished already closes these, but a frame could be
            // in flight. Never fall back to query-string identity.
            log.warn("WS frame dropped: sessionId={} has no resolvable identity", session.getId());
            return;
        }

        var payload = objectMapper.readValue(message.getPayload(), Map.class);
        String type = (String) payload.get("type");

        if (ChatSocketFrame.TYPE_MESSAGE.equals(type)) {
            String text = (String) payload.get("text");
            if (text == null || text.isBlank()) {
                log.debug("WS frame dropped: sessionId={} sent a MESSAGE with no text", session.getId());
                return;
            }
            Message saved = chatService.sendMessage(
                    participants.userId(), participants.advisorId(), text, participants.senderType());
            // The entity itself is never broadcast: it has no `type` discriminator for clients to
            // switch on, and its shape is the Cassandra primary key's, not the API's.
            broadcastToConversation(
                    participants.userId(),
                    participants.advisorId(),
                    ChatSocketFrame.MessageFrame.of(MessageResponse.from(saved)));
        } else if (ChatSocketFrame.TYPE_TYPING.equals(type)) {
            broadcastToConversation(
                    participants.userId(),
                    participants.advisorId(),
                    ChatSocketFrame.TypingFrame.of(participants.callerId()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        conversationSessions.values().forEach(sessions -> sessions.remove(session.getId()));
        log.info("WS disconnected: sessionId={} status={}", session.getId(), status);
    }

    /**
     * Fans a frame out to every socket in the conversation.
     *
     * <p>Takes a {@link ChatSocketFrame} rather than an arbitrary object so the compiler enforces
     * that whatever goes on the wire carries a {@code type} discriminator and the field names
     * clients actually read.
     */
    public void broadcastToConversation(UUID userId, UUID advisorId, ChatSocketFrame payload) {
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

    /**
     * Derives both participants from the verified handshake attributes plus the counterparty
     * query parameter.
     *
     * <p>Which side of the conversation the caller occupies is decided by
     * {@link ConversationParticipants#resolve(UUID, String, UUID)} — the same rule the REST
     * controller applies to its {@code X-User-Role} header, kept in one place so the two entry
     * points cannot drift into reading different partitions for the same caller. All this method
     * adds is the socket-specific plumbing: where the verified identity lives, and which query
     * parameter names the counterparty for a given role.
     *
     * @return {@code null} if the session carries no verified identity, carries a role that is
     *         not a chat participant, or names no parseable counterparty
     */
    static ConversationParticipants resolveParticipants(WebSocketSession session) {
        Map<String, Object> attributes = session.getAttributes();
        if (attributes == null) {
            return null;
        }
        if (!(attributes.get(JwtHandshakeInterceptor.ATTR_USER_ID) instanceof String subject)
                || !(attributes.get(JwtHandshakeInterceptor.ATTR_ROLE) instanceof String role)) {
            return null;
        }

        return ConversationParticipants.resolve(parseUuid(subject), role, parseUuid(counterparty(session, role)));
    }

    /**
     * The counterparty parameter a caller in this role is expected to supply. A role that is not
     * a chat participant names no counterparty at all, and {@code resolve} then refuses it.
     */
    private static String counterparty(WebSocketSession session, String role) {
        if (ConversationParticipants.ROLE_USER.equals(role)) {
            return queryParam(session, PARAM_ADVISOR_ID);
        }
        if (ConversationParticipants.ROLE_ADVISOR.equals(role)) {
            return queryParam(session, PARAM_USER_ID);
        }
        return null;
    }

    /** Reads a counterparty id from the query string. Never used for the caller's own identity. */
    private static String queryParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Canonical key: lexicographically smaller UUID first so that
     * user→advisor and advisor→user map to the same bucket.
     */
    private static String conversationKey(UUID userId, UUID advisorId) {
        String user = userId.toString();
        String advisor = advisorId.toString();
        return user.compareTo(advisor) < 0
                ? user + ":" + advisor
                : advisor + ":" + user;
    }
}
