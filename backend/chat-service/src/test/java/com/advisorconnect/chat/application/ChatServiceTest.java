package com.advisorconnect.chat.application;

import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.domain.port.out.ConversationRepository;
import com.advisorconnect.chat.domain.port.out.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The two rules that make the unread badge correct, and the one that stops it breaking chat.
 *
 * <p>Everything here is about the seam between the {@code messages} table and the two denormalised
 * inbox tables. Cassandra cannot write all three atomically, so the service is what decides
 * <em>who</em> gets an unread increment and what happens when the summary write fails — neither is
 * expressible in the schema, so neither is covered by the Testcontainers IT's schema assertions.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UUID USER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ADVISOR = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(messageRepository, conversationRepository);
    }

    /** The repository persists whatever it is handed, as a real Cassandra insert would. */
    private void repositoryEchoesSavedMessage() {
        given(messageRepository.save(any(Message.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    // ── the message row itself ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("the persisted message")
    class PersistedMessage {

        @Test
        @DisplayName("a seeker's message is attributed to the seeker, not to the partition's advisor")
        void userSenderIsAttributedToTheUser() {
            repositoryEchoesSavedMessage();

            Message saved = chatService.sendMessage(USER, ADVISOR, "hello", "user");

            assertThat(saved.getSenderId()).isEqualTo(USER);
            assertThat(saved.getSenderType()).isEqualTo("user");
            assertThat(saved.getUserId()).isEqualTo(USER);
            assertThat(saved.getAdvisorId()).isEqualTo(ADVISOR);
        }

        @Test
        @DisplayName("an advisor's message is attributed to the advisor, same partition")
        void advisorSenderIsAttributedToTheAdvisor() {
            repositoryEchoesSavedMessage();

            Message saved = chatService.sendMessage(USER, ADVISOR, "hi back", "advisor");

            assertThat(saved.getSenderId()).isEqualTo(ADVISOR);
            assertThat(saved.getSenderType()).isEqualTo("advisor");
            // The partition key does not flip with the sender — that is the whole point of it
            // being positional rather than sender/recipient.
            assertThat(saved.getUserId()).isEqualTo(USER);
            assertThat(saved.getAdvisorId()).isEqualTo(ADVISOR);
        }

        @Test
        @DisplayName("a new message starts unread and carries a fresh id and timestamp")
        void newMessageStartsUnread() {
            repositoryEchoesSavedMessage();
            Instant before = Instant.now();

            Message saved = chatService.sendMessage(USER, ADVISOR, "hello", "user");

            assertThat(saved.isRead()).isFalse();
            assertThat(saved.getMessageId()).isNotNull();
            assertThat(saved.getCreatedAt()).isBetween(before, Instant.now());
        }
    }

    // ── the asymmetry that makes the badge mean something ────────────────────────────────────

    @Nested
    @DisplayName("unread counting")
    class UnreadCounting {

        @Test
        @DisplayName("a seeker's message increments the advisor's badge and never the seeker's own")
        void seekerMessageIncrementsOnlyTheAdvisor() {
            repositoryEchoesSavedMessage();

            Message saved = chatService.sendMessage(USER, ADVISOR, "hello", "user");

            // Recipient (the advisor) earns the badge...
            verify(conversationRepository).recordAdvisorConversationActivity(
                    ADVISOR, USER, "hello", saved.getCreatedAt(), true);
            // ...and the sender's own inbox is refreshed without one.
            verify(conversationRepository).recordUserConversationActivity(
                    USER, ADVISOR, "hello", saved.getCreatedAt(), false);
        }

        @Test
        @DisplayName("an advisor's message increments the seeker's badge and never the advisor's own")
        void advisorMessageIncrementsOnlyTheSeeker() {
            repositoryEchoesSavedMessage();

            Message saved = chatService.sendMessage(USER, ADVISOR, "hi back", "advisor");

            verify(conversationRepository).recordUserConversationActivity(
                    USER, ADVISOR, "hi back", saved.getCreatedAt(), true);
            verify(conversationRepository).recordAdvisorConversationActivity(
                    ADVISOR, USER, "hi back", saved.getCreatedAt(), false);
        }

        @Test
        @DisplayName("both inboxes are refreshed for every message, so neither preview goes stale")
        void bothInboxesAreAlwaysRefreshed() {
            repositoryEchoesSavedMessage();

            chatService.sendMessage(USER, ADVISOR, "hello", "user");

            InOrder order = inOrder(messageRepository, conversationRepository);
            // The message is authoritative and must land first; the summaries derive from it.
            order.verify(messageRepository).save(any(Message.class));
            order.verify(conversationRepository).recordUserConversationActivity(
                    any(), any(), anyString(), any(), anyBoolean());
            order.verify(conversationRepository).recordAdvisorConversationActivity(
                    any(), any(), anyString(), any(), anyBoolean());
        }
    }

    // ── the summary write must never be able to break chat ───────────────────────────────────

    @Nested
    @DisplayName("summary-write failures are contained")
    class SummaryFailures {

        @Test
        @DisplayName("a failed seeker-inbox write neither fails the send nor loses the message")
        void userSummaryFailureDoesNotFailTheSend() {
            repositoryEchoesSavedMessage();
            willThrow(new IllegalStateException("cassandra unavailable"))
                    .given(conversationRepository)
                    .recordUserConversationActivity(any(), any(), anyString(), any(), anyBoolean());

            Message saved = chatService.sendMessage(USER, ADVISOR, "hello", "user");

            // The message is still returned to the caller — it really was persisted, and telling
            // the sender otherwise would be a lie the WebSocket path cannot even roll back.
            assertThat(saved).isNotNull();
            assertThat(saved.getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("one inbox failing still leaves the other inbox updated")
        void oneSideFailingDoesNotCostTheOtherItsUpdate() {
            repositoryEchoesSavedMessage();
            willThrow(new IllegalStateException("cassandra unavailable"))
                    .given(conversationRepository)
                    .recordUserConversationActivity(any(), any(), anyString(), any(), anyBoolean());

            chatService.sendMessage(USER, ADVISOR, "hello", "user");

            // Attempted independently, not skipped because the first one threw.
            verify(conversationRepository).recordAdvisorConversationActivity(
                    eq(ADVISOR), eq(USER), eq("hello"), any(), eq(true));
        }

        @Test
        @DisplayName("both inboxes failing still yields a successful send")
        void bothSidesFailingStillSucceeds() {
            repositoryEchoesSavedMessage();
            willThrow(new IllegalStateException("down"))
                    .given(conversationRepository)
                    .recordUserConversationActivity(any(), any(), anyString(), any(), anyBoolean());
            willThrow(new IllegalStateException("down"))
                    .given(conversationRepository)
                    .recordAdvisorConversationActivity(any(), any(), anyString(), any(), anyBoolean());

            assertThatCode(() -> chatService.sendMessage(USER, ADVISOR, "hello", "user"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("a failed message write does propagate — that one really is a lost message")
        void messageWriteFailureStillPropagates() {
            willThrow(new IllegalStateException("cassandra unavailable"))
                    .given(messageRepository).save(any(Message.class));

            assertThatCode(() -> chatService.sendMessage(USER, ADVISOR, "hello", "user"))
                    .isInstanceOf(IllegalStateException.class);

            // Nothing to summarise if nothing was stored.
            verify(conversationRepository, never())
                    .recordUserConversationActivity(any(), any(), anyString(), any(), anyBoolean());
        }
    }

    // ── mark-as-read ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mark-as-read")
    class MarkAsRead {

        private ConversationParticipants asUser() {
            return ConversationParticipants.resolve(USER, ConversationParticipants.ROLE_USER, ADVISOR);
        }

        private ConversationParticipants asAdvisor() {
            return ConversationParticipants.resolve(ADVISOR, ConversationParticipants.ROLE_ADVISOR, USER);
        }

        @Test
        @DisplayName("a seeker clears only their own badge, never the advisor's")
        void seekerClearsOnlyTheirOwnBadge() {
            given(messageRepository.markConversationRead(USER, ADVISOR)).willReturn(3);

            assertThat(chatService.markConversationRead(asUser())).isEqualTo(3);

            verify(conversationRepository).clearUserUnread(USER, ADVISOR);
            // Clearing the advisor's badge would tell them their own unread messages were seen.
            verify(conversationRepository, never()).clearAdvisorUnread(any(), any());
        }

        @Test
        @DisplayName("an advisor clears only their own badge, never the seeker's")
        void advisorClearsOnlyTheirOwnBadge() {
            given(messageRepository.markConversationRead(USER, ADVISOR)).willReturn(2);

            assertThat(chatService.markConversationRead(asAdvisor())).isEqualTo(2);

            verify(conversationRepository).clearAdvisorUnread(ADVISOR, USER);
            verify(conversationRepository, never()).clearUserUnread(any(), any());
        }

        @Test
        @DisplayName("both roles address the same partition, in the same (user, advisor) order")
        void bothRolesReadTheSamePartition() {
            given(messageRepository.markConversationRead(USER, ADVISOR)).willReturn(0);

            chatService.markConversationRead(asUser());
            chatService.markConversationRead(asAdvisor());

            // Two calls, both with the arguments in partition-key order — the whole reason
            // ConversationParticipants exists.
            verify(messageRepository, org.mockito.Mockito.times(2)).markConversationRead(USER, ADVISOR);
        }

        @Test
        @DisplayName("a failed badge clear still reports the messages that were marked read")
        void badgeClearFailureDoesNotFailTheRequest() {
            given(messageRepository.markConversationRead(USER, ADVISOR)).willReturn(4);
            willThrow(new IllegalStateException("cassandra unavailable"))
                    .given(conversationRepository).clearUserUnread(any(), any());

            // The messages table is authoritative and was updated; the badge is a derived hint.
            assertThat(chatService.markConversationRead(asUser())).isEqualTo(4);
        }
    }
}
