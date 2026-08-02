package com.advisorconnect.chat.adapter.in.web;

import com.advisorconnect.chat.application.ChatService;
import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationByUser;
import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.infrastructure.config.SecurityConfig;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST contract, and the authorisation that guards it.
 *
 * <p>Two things are under test that nothing else covers. First, that {@code SecurityConfig} really
 * is wired in — before it existed the entire chat history was reachable with no credential at all.
 * Second, that the {@code counterpartyId} path variable lands on the correct half of the Cassandra
 * partition key for each role: getting that backwards would not error, it would silently read a
 * different (empty, or worse, someone else's) conversation.
 */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ADVISOR = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ADMIN = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    /**
     * With no login mechanism configured, Spring Security's default entry point answers an
     * unauthenticated request with 403 rather than 401. Either is a correct "you may not", and
     * pinning the exact code would make this test a change-detector for a Spring default.
     */
    private static Matcher<Integer> rejected() {
        return Matchers.isOneOf(401, 403);
    }

    private static Message message(String text) {
        return Message.builder()
                .userId(USER).advisorId(ADVISOR)
                .messageId(UUID.fromString("44444444-4444-4444-8444-444444444444"))
                .senderId(USER).senderType("user")
                .text(text)
                .createdAt(Instant.parse("2026-07-31T18:00:00Z"))
                .read(false)
                .build();
    }

    // ── the authentication gate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unauthenticated requests")
    class Unauthenticated {

        @Test
        @DisplayName("message history without identity headers is refused")
        void messageHistoryIsRefused() throws Exception {
            mockMvc.perform(get("/chats/{id}/messages", ADVISOR))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("the conversation list without identity headers is refused")
        void conversationListIsRefused() throws Exception {
            mockMvc.perform(get("/chats"))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("the advisor inbox without identity headers is refused")
        void advisorInboxIsRefused() throws Exception {
            mockMvc.perform(get("/chats/advisor-inbox"))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("mark-as-read without identity headers is refused")
        void markReadIsRefused() throws Exception {
            mockMvc.perform(put("/chats/{id}/read", ADVISOR))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("a role header alone, with no user id, still fails closed")
        void roleWithoutUserIdFailsClosed() throws Exception {
            mockMvc.perform(get("/chats").header("X-User-Role", "USER"))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("a non-UUID user id fails closed rather than 500-ing")
        void malformedUserIdFailsClosed() throws Exception {
            mockMvc.perform(get("/chats")
                            .header("X-User-Id", "not-a-uuid")
                            .header("X-User-Role", "USER"))
                    .andExpect(status().is(rejected()));

            verifyNoInteractions(chatService);
        }
    }

    // ── message history, and which partition it reads ────────────────────────────────────────

    @Nested
    @DisplayName("GET /chats/{counterpartyId}/messages")
    class MessageHistory {

        @Test
        @DisplayName("a seeker's counterparty is read as the advisor half of the partition key")
        void seekerCounterpartyIsTheAdvisor() throws Exception {
            given(chatService.getMessages(USER, ADVISOR, 50)).willReturn(List.of(message("hello")));

            mockMvc.perform(get("/chats/{id}/messages", ADVISOR)
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].text").value("hello"))
                    .andExpect(jsonPath("$[0].senderType").value("user"))
                    .andExpect(jsonPath("$[0].id").value("44444444-4444-4444-8444-444444444444"))
                    .andExpect(jsonPath("$[0].conversationId")
                            .value(ConversationParticipants.conversationId(USER, ADVISOR)));

            verify(chatService).getMessages(USER, ADVISOR, 50);
        }

        @Test
        @DisplayName("an advisor's counterparty is read as the seeker half — the ids swap sides")
        void advisorCounterpartyIsTheSeeker() throws Exception {
            given(chatService.getMessages(USER, ADVISOR, 50)).willReturn(List.of());

            // Same conversation, opposite caller: the advisor names the *user* in the path.
            mockMvc.perform(get("/chats/{id}/messages", USER)
                            .header("X-User-Id", ADVISOR)
                            .header("X-User-Role", "ADVISOR"))
                    .andExpect(status().isOk());

            // Crucially still (USER, ADVISOR) — not (ADVISOR, USER).
            verify(chatService).getMessages(USER, ADVISOR, 50);
        }

        @Test
        @DisplayName("an explicit limit reaches the service")
        void explicitLimitIsHonoured() throws Exception {
            given(chatService.getMessages(any(), any(), anyInt())).willReturn(List.of());

            mockMvc.perform(get("/chats/{id}/messages", ADVISOR)
                            .param("limit", "10")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk());

            verify(chatService).getMessages(USER, ADVISOR, 10);
        }

        @Test
        @DisplayName("limit=0 is clamped to 1, because Cassandra rejects LIMIT 0 outright")
        void zeroLimitIsClampedUp() throws Exception {
            given(chatService.getMessages(any(), any(), anyInt())).willReturn(List.of());

            mockMvc.perform(get("/chats/{id}/messages", ADVISOR)
                            .param("limit", "0")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk());

            verify(chatService).getMessages(USER, ADVISOR, 1);
        }

        @Test
        @DisplayName("an oversized limit is clamped down rather than pulling a whole partition")
        void oversizedLimitIsClampedDown() throws Exception {
            given(chatService.getMessages(any(), any(), anyInt())).willReturn(List.of());

            mockMvc.perform(get("/chats/{id}/messages", ADVISOR)
                            .param("limit", "100000")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
            verify(chatService).getMessages(any(), any(), limit.capture());
            assertThat(limit.getValue()).isEqualTo(ChatController.MAX_LIMIT);
        }

        @Test
        @DisplayName("an ADMIN is authenticated but is not a chat participant — 403")
        void adminIsNotAParticipant() throws Exception {
            mockMvc.perform(get("/chats/{id}/messages", ADVISOR)
                            .header("X-User-Id", ADMIN)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("a non-UUID counterparty is a bad request, not a server error")
        void malformedCounterpartyIsRejected() throws Exception {
            mockMvc.perform(get("/chats/{id}/messages", "not-a-uuid")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(chatService);
        }
    }

    // ── the two inboxes ──────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("conversation lists")
    class ConversationLists {

        @Test
        @DisplayName("a seeker gets their conversation list with the display fields intact")
        void seekerGetsTheirList() throws Exception {
            given(chatService.getUserConversations(USER)).willReturn(List.of(
                    ConversationByUser.builder()
                            .userId(USER).advisorId(ADVISOR)
                            .advisorUsername("maya_chen").advisorColor("#005e8f")
                            .lastMessage("talk Thursday")
                            .lastMessageAt(Instant.parse("2026-07-31T18:04:00Z"))
                            .unreadCount(2).advisorOnline(true)
                            .build()));

            mockMvc.perform(get("/chats")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].advisorId").value(ADVISOR.toString()))
                    .andExpect(jsonPath("$[0].advisorUsername").value("maya_chen"))
                    .andExpect(jsonPath("$[0].unreadCount").value(2))
                    // The web client reads this exact field name off the wire.
                    .andExpect(jsonPath("$[0].isAdvisorOnline").value(true));
        }

        @Test
        @DisplayName("an advisor cannot use the seeker conversation list")
        void advisorIsRefusedTheSeekerList() throws Exception {
            mockMvc.perform(get("/chats")
                            .header("X-User-Id", ADVISOR)
                            .header("X-User-Role", "ADVISOR"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("an advisor gets their inbox")
        void advisorGetsTheirInbox() throws Exception {
            given(chatService.getAdvisorConversations(ADVISOR)).willReturn(List.of(
                    ConversationByAdvisor.builder()
                            .advisorId(ADVISOR).userId(USER)
                            .lastMessage("is Thursday okay?")
                            .lastMessageAt(Instant.parse("2026-07-31T18:00:00Z"))
                            .unreadCount(1)
                            .build()));

            mockMvc.perform(get("/chats/advisor-inbox")
                            .header("X-User-Id", ADVISOR)
                            .header("X-User-Role", "ADVISOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].userId").value(USER.toString()))
                    .andExpect(jsonPath("$[0].unreadCount").value(1))
                    .andExpect(jsonPath("$[0].id")
                            .value(ConversationParticipants.conversationId(USER, ADVISOR)));
        }

        @Test
        @DisplayName("a seeker cannot use the advisor inbox")
        void seekerIsRefusedTheAdvisorInbox() throws Exception {
            mockMvc.perform(get("/chats/advisor-inbox")
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(chatService);
        }

        @Test
        @DisplayName("'advisor-inbox' is not swallowed by the {counterpartyId} mapping")
        void advisorInboxIsNotShadowed() throws Exception {
            given(chatService.getAdvisorConversations(ADVISOR)).willReturn(List.of());

            mockMvc.perform(get("/chats/advisor-inbox")
                            .header("X-User-Id", ADVISOR)
                            .header("X-User-Role", "ADVISOR"))
                    .andExpect(status().isOk());

            // Had the literal segment lost to the path variable, this would have 400'd trying to
            // parse "advisor-inbox" as a UUID.
            verify(chatService).getAdvisorConversations(ADVISOR);
        }
    }

    // ── mark-as-read ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /chats/{counterpartyId}/read")
    class MarkRead {

        @Test
        @DisplayName("a seeker marks the conversation read and is told how many messages moved")
        void seekerMarksRead() throws Exception {
            given(chatService.markConversationRead(any())).willReturn(3);

            mockMvc.perform(put("/chats/{id}/read", ADVISOR)
                            .header("X-User-Id", USER)
                            .header("X-User-Role", "USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messagesMarkedRead").value(3));

            ArgumentCaptor<ConversationParticipants> captor =
                    ArgumentCaptor.forClass(ConversationParticipants.class);
            verify(chatService).markConversationRead(captor.capture());

            ConversationParticipants participants = captor.getValue();
            assertThat(participants.userId()).isEqualTo(USER);
            assertThat(participants.advisorId()).isEqualTo(ADVISOR);
            assertThat(participants.callerIsUser()).isTrue();
        }

        @Test
        @DisplayName("an advisor marking the same conversation read is placed on the advisor side")
        void advisorMarksRead() throws Exception {
            given(chatService.markConversationRead(any())).willReturn(0);

            mockMvc.perform(put("/chats/{id}/read", USER)
                            .header("X-User-Id", ADVISOR)
                            .header("X-User-Role", "ADVISOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.messagesMarkedRead").value(0));

            ArgumentCaptor<ConversationParticipants> captor =
                    ArgumentCaptor.forClass(ConversationParticipants.class);
            verify(chatService).markConversationRead(captor.capture());

            ConversationParticipants participants = captor.getValue();
            // Same partition as the seeker's call above — only the caller's side differs.
            assertThat(participants.userId()).isEqualTo(USER);
            assertThat(participants.advisorId()).isEqualTo(ADVISOR);
            assertThat(participants.callerIsUser()).isFalse();
        }

        @Test
        @DisplayName("an ADMIN cannot mark anyone's conversation read")
        void adminCannotMarkRead() throws Exception {
            mockMvc.perform(put("/chats/{id}/read", ADVISOR)
                            .header("X-User-Id", ADMIN)
                            .header("X-User-Role", "ADMIN"))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(chatService);
        }
    }
}
