package com.advisorconnect.chat.adapter.in.websocket;

import com.advisorconnect.chat.application.ChatService;
import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.infrastructure.websocket.JwtHandshakeInterceptor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.withSettings;

/**
 * Guards the identity-resolution rule that {@link ChatWebSocketHandler} exists to enforce:
 * the caller's own id comes from the JWT-derived session attributes and nowhere else.
 *
 * <p>Every test therefore puts a <em>conflicting</em> id in the query string. Before this was
 * fixed the handler read both ids off the query string, so a client could impersonate anyone by
 * editing the URL; these tests fail loudly if that ever regresses.
 */
class ChatWebSocketHandlerTest {

    private static final UUID TRUSTED_USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TRUSTED_ADVISOR = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SPOOFED_USER = UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID SPOOFED_ADVISOR = UUID.fromString("88888888-8888-8888-8888-888888888888");

    private ChatService chatService;
    private ObjectMapper objectMapper;
    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        // Mirrors Boot's auto-configured mapper: createdAt is an Instant, which a bare
        // ObjectMapper cannot serialise at all, and which Boot renders as an ISO-8601 string
        // rather than the module's default epoch decimal. The frames these tests assert on are
        // the literal bytes the browser parses, so that distinction is part of the contract.
        objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        handler = new ChatWebSocketHandler(chatService, objectMapper);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────

    /**
     * @param subject the id the interceptor verified from the JWT — the trustworthy one
     * @param role    the verified role claim, {@code null} to simulate an unauthenticated session
     * @param query   the raw query string, deliberately carrying contradictory ids
     */
    private static WebSocketSession session(String id, UUID subject, String role, String query) {
        WebSocketSession session = mock(WebSocketSession.class, withSettings().lenient());
        Map<String, Object> attributes = new HashMap<>();
        if (subject != null) {
            attributes.put(JwtHandshakeInterceptor.ATTR_USER_ID, subject.toString());
        }
        if (role != null) {
            attributes.put(JwtHandshakeInterceptor.ATTR_ROLE, role);
        }
        given(session.getId()).willReturn(id);
        given(session.getAttributes()).willReturn(attributes);
        given(session.getUri()).willReturn(URI.create("ws://chat-service:8085/ws/chat?" + query));
        given(session.isOpen()).willReturn(true);
        return session;
    }

    /** A user-role session whose query string lies about who the user is. */
    private static WebSocketSession spoofingUserSession(String id) {
        return session(id, TRUSTED_USER, "USER",
                "userId=" + SPOOFED_USER + "&advisorId=" + TRUSTED_ADVISOR + "&token=irrelevant");
    }

    /** An advisor-role session whose query string lies about who the advisor is. */
    private static WebSocketSession spoofingAdvisorSession(String id) {
        return session(id, TRUSTED_ADVISOR, "ADVISOR",
                "advisorId=" + SPOOFED_ADVISOR + "&userId=" + TRUSTED_USER + "&token=irrelevant");
    }

    private static TextMessage chatFrame(String text) {
        return new TextMessage("{\"type\":\"MESSAGE\",\"text\":\"" + text + "\"}");
    }

    private Message stubSave() {
        Message saved = Message.builder()
                .userId(TRUSTED_USER)
                .advisorId(TRUSTED_ADVISOR)
                .senderId(TRUSTED_USER)
                .senderType("user")
                .text("hello")
                .messageId(UUID.randomUUID())
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .read(false)
                .build();
        given(chatService.sendMessage(any(), any(), anyString(), anyString())).willReturn(saved);
        return saved;
    }

    /** The last frame delivered to a session, parsed as the browser would parse it. */
    private JsonNode lastFrameSentTo(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(sent.capture());
        return objectMapper.readTree(sent.getValue().getPayload());
    }

    // ── the core regression ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("the caller's own id always comes from the verified JWT, never the query string")
    class TrustedIdentityWins {

        @Test
        @DisplayName("a USER-role caller is the JWT subject, even when ?userId= says otherwise")
        void userRoleIgnoresSpoofedUserIdParam() throws Exception {
            stubSave();
            WebSocketSession session = spoofingUserSession("s1");

            handler.afterConnectionEstablished(session);
            handler.handleTextMessage(session, chatFrame("hello"));

            verify(chatService).sendMessage(TRUSTED_USER, TRUSTED_ADVISOR, "hello", "user");
            verify(chatService, never()).sendMessage(
                    org.mockito.ArgumentMatchers.eq(SPOOFED_USER), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("an ADVISOR-role caller is the JWT subject, even when ?advisorId= says otherwise")
        void advisorRoleIgnoresSpoofedAdvisorIdParam() throws Exception {
            stubSave();
            WebSocketSession session = spoofingAdvisorSession("s2");

            handler.afterConnectionEstablished(session);
            handler.handleTextMessage(session, chatFrame("hello"));

            // userId comes from the query string (legitimately — the JWT only proves the caller),
            // advisorId comes from the verified subject.
            verify(chatService).sendMessage(TRUSTED_USER, TRUSTED_ADVISOR, "hello", "advisor");
            verify(chatService, never()).sendMessage(
                    any(), org.mockito.ArgumentMatchers.eq(SPOOFED_ADVISOR), anyString(), anyString());
        }

        @Test
        @DisplayName("a TYPING notice is attributed to the verified subject, not the query string")
        void typingUsesTrustedSenderId() throws Exception {
            WebSocketSession session = spoofingUserSession("s3");
            handler.afterConnectionEstablished(session);

            handler.handleTextMessage(session, new TextMessage("{\"type\":\"TYPING\"}"));

            JsonNode frame = lastFrameSentTo(session);
            assertThat(frame.path("senderId").asText()).isEqualTo(TRUSTED_USER.toString());
            assertThat(frame.toString()).doesNotContain(SPOOFED_USER.toString());
        }

        @Test
        @DisplayName("a TYPING notice goes out as a flat type-discriminated frame")
        void typingFrameIsFlatAndDiscriminated() throws Exception {
            WebSocketSession session = spoofingUserSession("s4");
            handler.afterConnectionEstablished(session);

            handler.handleTextMessage(session, new TextMessage("{\"type\":\"TYPING\"}"));

            JsonNode frame = lastFrameSentTo(session);
            // Clients switch on a top-level `type`; nesting the payload under an envelope key
            // would leave them reading `undefined` for every field.
            assertThat(frame.path("type").asText()).isEqualTo("TYPING");
            assertThat(frame.properties()).map(Map.Entry::getKey)
                    .containsExactlyInAnyOrder("type", "senderId");
        }
    }

    // ── conversation routing ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("conversation routing")
    class Routing {

        @Test
        @DisplayName("delivers a saved message to both participants, who joined from opposite sides")
        void bothSidesShareOneConversationBucket() throws Exception {
            Message saved = stubSave();
            WebSocketSession userSession = spoofingUserSession("user-session");
            WebSocketSession advisorSession = spoofingAdvisorSession("advisor-session");

            handler.afterConnectionEstablished(userSession);
            handler.afterConnectionEstablished(advisorSession);
            handler.handleTextMessage(userSession, chatFrame("hello"));

            JsonNode toUser = lastFrameSentTo(userSession);
            JsonNode toAdvisor = lastFrameSentTo(advisorSession);
            assertThat(toUser).isEqualTo(toAdvisor);
            assertMessageFrame(toUser, saved);
        }

        @Test
        @DisplayName("broadcasts the API's message shape, not the Cassandra row")
        void broadcastIsTheApiShape() throws Exception {
            Message saved = stubSave();
            WebSocketSession session = spoofingUserSession("s");
            handler.afterConnectionEstablished(session);

            handler.handleTextMessage(session, chatFrame("hello"));

            JsonNode frame = lastFrameSentTo(session);
            // The entity's partition-key fields are storage layout, not protocol, and the client
            // has no parser for them. Broadcasting the entity also omitted `type` entirely, which
            // made every live message unroutable on the client.
            assertThat(frame.properties()).map(Map.Entry::getKey).containsExactlyInAnyOrder(
                    "type", "id", "conversationId", "senderId", "senderType", "text", "createdAt", "read");
            assertMessageFrame(frame, saved);
        }

        /** The exact contract the browser client parses: flat, {@code type}-discriminated. */
        private void assertMessageFrame(JsonNode frame, Message saved) {
            assertThat(frame.path("type").asText()).isEqualTo("MESSAGE");
            assertThat(frame.path("id").asText()).isEqualTo(saved.getMessageId().toString());
            assertThat(frame.path("conversationId").asText())
                    .isEqualTo(TRUSTED_USER + ":" + TRUSTED_ADVISOR);
            assertThat(frame.path("senderId").asText()).isEqualTo(TRUSTED_USER.toString());
            assertThat(frame.path("senderType").asText()).isEqualTo("user");
            assertThat(frame.path("text").asText()).isEqualTo("hello");
            // An ISO-8601 string, not an epoch number: the client feeds this straight to Date.
            assertThat(frame.path("createdAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
        }

        @Test
        @DisplayName("stops delivering to a session once it has closed")
        void closedSessionIsUnsubscribed() throws Exception {
            stubSave();
            WebSocketSession userSession = spoofingUserSession("user-session");
            WebSocketSession advisorSession = spoofingAdvisorSession("advisor-session");
            handler.afterConnectionEstablished(userSession);
            handler.afterConnectionEstablished(advisorSession);

            handler.afterConnectionClosed(advisorSession, CloseStatus.NORMAL);
            handler.handleTextMessage(userSession, chatFrame("hello"));

            verify(advisorSession, never()).sendMessage(any());
            verify(userSession).sendMessage(any());
        }
    }

    // ── refused sessions ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("a session that cannot be attributed to a conversation is closed and cannot send")
    class Refused {

        @Test
        @DisplayName("when the handshake attributes carry no verified identity at all")
        void noAttributes() throws Exception {
            WebSocketSession session = session("s", null, null,
                    "userId=" + SPOOFED_USER + "&advisorId=" + SPOOFED_ADVISOR);

            assertRefused(session);
        }

        @Test
        @DisplayName("when a subject is present but the role claim is missing")
        void missingRole() throws Exception {
            WebSocketSession session = session("s", TRUSTED_USER, null, "advisorId=" + TRUSTED_ADVISOR);

            assertRefused(session);
        }

        @ParameterizedTest(name = "when the verified role is {0}, which is not a chat participant")
        @ValueSource(strings = {"ADMIN", "user", "advisor", "SUPPORT"})
        void nonParticipantRoles(String role) throws Exception {
            // Casing matters: auth-service issues UserRole.name(), so only the uppercase
            // constants are participants. A lowercase "user" is not a role this system mints.
            WebSocketSession session = session("s", TRUSTED_USER, role,
                    "advisorId=" + TRUSTED_ADVISOR + "&userId=" + TRUSTED_USER);

            assertRefused(session);
        }

        @Test
        @DisplayName("when the USER caller names no counterparty advisor")
        void userWithoutAdvisorIdParam() throws Exception {
            WebSocketSession session = session("s", TRUSTED_USER, "USER", "token=irrelevant");

            assertRefused(session);
        }

        @Test
        @DisplayName("when the ADVISOR caller names no counterparty user")
        void advisorWithoutUserIdParam() throws Exception {
            WebSocketSession session = session("s", TRUSTED_ADVISOR, "ADVISOR", "token=irrelevant");

            assertRefused(session);
        }

        @Test
        @DisplayName("when the counterparty id is not a UUID")
        void unparseableCounterparty() throws Exception {
            WebSocketSession session = session("s", TRUSTED_USER, "USER", "advisorId=not-a-uuid");

            assertRefused(session);
        }

        @Test
        @DisplayName("when the verified subject itself is not a UUID")
        void unparseableSubject() throws Exception {
            WebSocketSession session = mock(WebSocketSession.class, withSettings().lenient());
            given(session.getId()).willReturn("s");
            given(session.getAttributes()).willReturn(new HashMap<>(Map.of(
                    JwtHandshakeInterceptor.ATTR_USER_ID, "not-a-uuid",
                    JwtHandshakeInterceptor.ATTR_ROLE, "USER")));
            given(session.getUri()).willReturn(
                    URI.create("ws://chat-service:8085/ws/chat?advisorId=" + TRUSTED_ADVISOR));
            given(session.isOpen()).willReturn(true);

            assertRefused(session);
        }

        private void assertRefused(WebSocketSession session) throws Exception {
            handler.afterConnectionEstablished(session);

            ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
            verify(session).close(status.capture());
            assertThat(status.getValue().getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());

            // Frames arriving on a refused socket must still never reach the domain.
            handler.handleTextMessage(session, chatFrame("hello"));
            verifyNoInteractions(chatService);
            verify(session, never()).sendMessage(any());
        }
    }

    // ── payload handling ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("payload handling")
    class Payloads {

        @Test
        @DisplayName("drops a MESSAGE with no text rather than persisting an empty row")
        void blankTextIsDropped() throws Exception {
            WebSocketSession session = spoofingUserSession("s");
            handler.afterConnectionEstablished(session);

            handler.handleTextMessage(session, new TextMessage("{\"type\":\"MESSAGE\",\"text\":\"   \"}"));
            handler.handleTextMessage(session, new TextMessage("{\"type\":\"MESSAGE\"}"));

            verify(chatService, never()).sendMessage(any(), any(), anyString(), anyString());
        }

        @Test
        @DisplayName("ignores an unrecognised frame type")
        void unknownTypeIsIgnored() throws Exception {
            WebSocketSession session = spoofingUserSession("s");
            handler.afterConnectionEstablished(session);

            handler.handleTextMessage(session, new TextMessage("{\"type\":\"WAT\"}"));

            verifyNoInteractions(chatService);
            verify(session, never()).sendMessage(any());
        }
    }

    @Test
    @DisplayName("getMessages is never invoked by the socket path (kept off the hot path)")
    void handlerDoesNotQueryHistory() throws Exception {
        stubSave();
        WebSocketSession session = spoofingUserSession("s");

        handler.afterConnectionEstablished(session);
        handler.handleTextMessage(session, chatFrame("hello"));

        verify(chatService, never()).getMessages(any(), any(), anyInt());
    }
}
