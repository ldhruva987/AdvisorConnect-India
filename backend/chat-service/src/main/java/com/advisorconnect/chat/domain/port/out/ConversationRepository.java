package com.advisorconnect.chat.domain.port.out;

import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationByUser;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The conversation-summary read model — the inbox previews behind {@code GET /chats} and
 * {@code GET /chats/advisor-inbox}.
 *
 * <p>Cassandra has no cross-table transaction and no server-side join, so the same fact ("a
 * message was sent") is written twice, once per query table. Both directions live behind this one
 * port rather than two, because they are always written together and a caller that updated only
 * one would leave the two inboxes disagreeing.
 *
 * <p>The two {@code record*Activity} methods are deliberately separate calls rather than one
 * combined one: the caller must be able to attempt the second even when the first fails, and each
 * side gets its own {@code incrementUnread} answer.
 */
public interface ConversationRepository {

    /** A seeker's inbox, most-recently-active first (the table's clustering order). */
    List<ConversationByUser> findConversationsForUser(UUID userId);

    /** An advisor's inbox, most-recently-active first. */
    List<ConversationByAdvisor> findConversationsForAdvisor(UUID advisorId);

    /**
     * Advances the seeker's inbox row for this conversation to a newly sent message.
     *
     * @param incrementUnread {@code true} only when the seeker is the <em>recipient</em>. A sender
     *                        never earns an unread badge for their own message.
     */
    void recordUserConversationActivity(UUID userId, UUID advisorId, String lastMessage,
                                        Instant lastMessageAt, boolean incrementUnread);

    /** Mirror of {@link #recordUserConversationActivity} for the advisor's inbox. */
    void recordAdvisorConversationActivity(UUID advisorId, UUID userId, String lastMessage,
                                           Instant lastMessageAt, boolean incrementUnread);

    /** Resets the seeker's unread badge for one conversation. A no-op if there is no such row. */
    void clearUserUnread(UUID userId, UUID advisorId);

    /** Resets the advisor's unread badge for one conversation. A no-op if there is no such row. */
    void clearAdvisorUnread(UUID advisorId, UUID userId);
}
