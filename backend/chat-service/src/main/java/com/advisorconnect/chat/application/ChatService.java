package com.advisorconnect.chat.application;

import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationByUser;
import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.domain.port.out.ConversationRepository;
import com.advisorconnect.chat.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    /**
     * Persists a message and then refreshes both parties' inbox previews.
     *
     * <p>Note the argument order: {@code userId} and {@code advisorId} are the two halves of the
     * Cassandra partition key and are <em>positional</em>, not "sender and recipient".
     * {@code senderType} is what says which of them sent this one. Callers that only know "the
     * caller and the other party" should go through
     * {@link ConversationParticipants#resolve(UUID, String, UUID)} first.
     */
    public Message sendMessage(UUID userId, UUID advisorId, String text, String senderType) {
        boolean sentByUser = ConversationParticipants.SENDER_TYPE_USER.equals(senderType);

        Message message = Message.builder()
                .userId(userId)
                .advisorId(advisorId)
                .senderId(sentByUser ? userId : advisorId)
                .senderType(senderType)
                .text(text)
                .messageId(UUID.randomUUID())
                .createdAt(Instant.now())
                .read(false)
                .build();

        Message saved = messageRepository.save(message);
        refreshConversationSummaries(saved, sentByUser);
        return saved;
    }

    public List<Message> getMessages(UUID userId, UUID advisorId, int limit) {
        return messageRepository.findByConversation(userId, advisorId, limit);
    }

    /** A seeker's inbox, most-recently-active first. */
    public List<ConversationByUser> getUserConversations(UUID userId) {
        return conversationRepository.findConversationsForUser(userId);
    }

    /** An advisor's inbox, most-recently-active first. */
    public List<ConversationByAdvisor> getAdvisorConversations(UUID advisorId) {
        return conversationRepository.findConversationsForAdvisor(advisorId);
    }

    /**
     * Marks a conversation read on behalf of one participant: every unread message gets
     * {@code read = true}, and that participant's unread badge is zeroed.
     *
     * <p>Only the <em>caller's</em> badge is cleared. Clearing both would tell the other party
     * their own unread messages had been seen.
     *
     * <p>The {@code messages} table carries a single {@code read} flag rather than one per side,
     * so "mark the conversation read" necessarily covers the caller's own sent messages too. That
     * is harmless — nobody renders an unread indicator on a message they sent — and is the only
     * behaviour the current schema can express.
     *
     * @return how many messages were flipped to read
     */
    public int markConversationRead(ConversationParticipants participants) {
        int marked = messageRepository.markConversationRead(
                participants.userId(), participants.advisorId());

        // Same eventual-consistency posture as the send path: the messages are authoritative, the
        // badge is a derived hint, and failing to clear the badge must not fail the request.
        try {
            if (participants.callerIsUser()) {
                conversationRepository.clearUserUnread(participants.userId(), participants.advisorId());
            } else {
                conversationRepository.clearAdvisorUnread(participants.advisorId(), participants.userId());
            }
        } catch (RuntimeException e) {
            log.warn("Marked {} message(s) read for conversation user={} advisor={}, but could not "
                            + "clear the unread badge: {}",
                    marked, participants.userId(), participants.advisorId(), e.toString());
        }
        return marked;
    }

    /**
     * Best-effort maintenance of the two denormalised inbox tables.
     *
     * <h2>Why failures are swallowed</h2>
     * Cassandra offers no transaction spanning {@code messages}, {@code conversations_by_user} and
     * {@code conversations_by_advisor}. A successful message write followed by a failed summary
     * write is therefore a reachable state, and the two views can disagree until the next message
     * or mark-as-read repairs them.
     *
     * <p>Given that, the only question is which way to fail. Propagating would mean a
     * <em>successfully persisted and already broadcast</em> chat message surfaces to the sender as
     * an error — the message is not lost, but the user is told it was, and over WebSocket there is
     * no rollback to perform either. Losing an inbox preview until the next message is plainly the
     * lesser harm, so the summary write can never break the send. The failure is logged loudly
     * enough to be alertable.
     *
     * <p>The two sides are attempted independently: one table being unavailable should not also
     * cost the other its update.
     */
    private void refreshConversationSummaries(Message saved, boolean sentByUser) {
        try {
            conversationRepository.recordUserConversationActivity(
                    saved.getUserId(), saved.getAdvisorId(), saved.getText(), saved.getCreatedAt(),
                    // The seeker's badge grows only when the advisor is the one who wrote.
                    !sentByUser);
        } catch (RuntimeException e) {
            log.warn("Message {} saved, but conversations_by_user was not updated for user={} advisor={}: {}",
                    saved.getMessageId(), saved.getUserId(), saved.getAdvisorId(), e.toString());
        }

        try {
            conversationRepository.recordAdvisorConversationActivity(
                    saved.getAdvisorId(), saved.getUserId(), saved.getText(), saved.getCreatedAt(),
                    // ...and the advisor's only when the seeker wrote.
                    sentByUser);
        } catch (RuntimeException e) {
            log.warn("Message {} saved, but conversations_by_advisor was not updated for advisor={} user={}: {}",
                    saved.getMessageId(), saved.getAdvisorId(), saved.getUserId(), e.toString());
        }
    }
}
