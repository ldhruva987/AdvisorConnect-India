package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationByUser;
import com.advisorconnect.chat.domain.port.out.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Cassandra adapter for the conversation-summary read model.
 *
 * <h2>Why an upsert is read-modify-delete-insert</h2>
 * Both summary tables put {@code last_message_at} in the primary key (it is the first clustering
 * column, so the inbox is sorted server-side). That makes an "update" of the last message a
 * <em>relocation</em>: the row's identity changes. Cassandra has no such operation, so each write
 * here:
 *
 * <ol>
 *   <li>reads the conversation's current row out of the caller's inbox partition — needed anyway,
 *       since {@code unread_count} must be incremented from its present value and Cassandra
 *       counters are not usable on a non-counter table;</li>
 *   <li>deletes that row if its timestamp is about to change;</li>
 *   <li>inserts the replacement.</li>
 * </ol>
 *
 * <p>Skipping step 2 was the obvious first implementation and is wrong: it accumulates one dead
 * row per message ever sent, and the inbox then shows the same conversation many times over.
 *
 * <h2>Concurrency</h2>
 * The read-modify-write is not atomic, so two messages landing in the same conversation at the
 * same instant can lose one increment. That is accepted: an unread badge is a hint, it is
 * corrected by the next {@code PUT /chats/{id}/read}, and the authoritative record is the
 * {@code messages} table's own {@code read} flag. Paying for LWT ({@code IF} clauses) on the chat
 * hot path to make a badge exact is not a trade worth making.
 *
 * <h2>Finding one conversation inside a partition</h2>
 * {@code advisor_id}/{@code user_id} is the <em>second</em> clustering column, so it cannot be
 * restricted without also restricting {@code last_message_at} — which is precisely the value we do
 * not know. The whole (inbox-sized, single-partition) row set is read and filtered in memory
 * instead of adding {@code ALLOW FILTERING}.
 */
@Repository
@RequiredArgsConstructor
public class CassandraConversationRepository implements ConversationRepository {

    private final SpringDataConversationByUserRepository byUserRepo;
    private final SpringDataConversationByAdvisorRepository byAdvisorRepo;

    @Override
    public List<ConversationByUser> findConversationsForUser(UUID userId) {
        return byUserRepo.findByUserId(userId);
    }

    @Override
    public List<ConversationByAdvisor> findConversationsForAdvisor(UUID advisorId) {
        return byAdvisorRepo.findByAdvisorId(advisorId);
    }

    @Override
    public void recordUserConversationActivity(UUID userId, UUID advisorId, String lastMessage,
                                               Instant lastMessageAt, boolean incrementUnread) {
        Instant at = toStoragePrecision(lastMessageAt);
        ConversationByUser existing = findUserConversation(userId, advisorId);

        // toBuilder() on the existing row carries the display columns
        // (advisorUsername/advisorColor/advisorOnline) forward untouched — this service does not
        // own them and must not blank them out.
        ConversationByUser.ConversationByUserBuilder builder = existing == null
                ? ConversationByUser.builder().userId(userId).advisorId(advisorId)
                : existing.toBuilder();

        ConversationByUser updated = builder
                .lastMessage(lastMessage)
                .lastMessageAt(at)
                .unreadCount(nextUnreadCount(existing == null ? 0 : existing.getUnreadCount(), incrementUnread))
                .build();

        if (existing != null && !at.equals(existing.getLastMessageAt())) {
            byUserRepo.delete(existing);
        }
        byUserRepo.save(updated);
    }

    @Override
    public void recordAdvisorConversationActivity(UUID advisorId, UUID userId, String lastMessage,
                                                  Instant lastMessageAt, boolean incrementUnread) {
        Instant at = toStoragePrecision(lastMessageAt);
        ConversationByAdvisor existing = findAdvisorConversation(advisorId, userId);

        ConversationByAdvisor.ConversationByAdvisorBuilder builder = existing == null
                ? ConversationByAdvisor.builder().advisorId(advisorId).userId(userId)
                : existing.toBuilder();

        ConversationByAdvisor updated = builder
                .lastMessage(lastMessage)
                .lastMessageAt(at)
                .unreadCount(nextUnreadCount(existing == null ? 0 : existing.getUnreadCount(), incrementUnread))
                .build();

        if (existing != null && !at.equals(existing.getLastMessageAt())) {
            byAdvisorRepo.delete(existing);
        }
        byAdvisorRepo.save(updated);
    }

    @Override
    public void clearUserUnread(UUID userId, UUID advisorId) {
        ConversationByUser existing = findUserConversation(userId, advisorId);
        if (existing == null || existing.getUnreadCount() == 0) {
            return;
        }
        // The primary key is unchanged, so this really is an in-place upsert — no delete needed.
        existing.setUnreadCount(0);
        byUserRepo.save(existing);
    }

    @Override
    public void clearAdvisorUnread(UUID advisorId, UUID userId) {
        ConversationByAdvisor existing = findAdvisorConversation(advisorId, userId);
        if (existing == null || existing.getUnreadCount() == 0) {
            return;
        }
        existing.setUnreadCount(0);
        byAdvisorRepo.save(existing);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────

    private ConversationByUser findUserConversation(UUID userId, UUID advisorId) {
        return byUserRepo.findByUserId(userId).stream()
                .filter(c -> advisorId.equals(c.getAdvisorId()))
                .findFirst()
                .orElse(null);
    }

    private ConversationByAdvisor findAdvisorConversation(UUID advisorId, UUID userId) {
        return byAdvisorRepo.findByAdvisorId(advisorId).stream()
                .filter(c -> userId.equals(c.getUserId()))
                .findFirst()
                .orElse(null);
    }

    private static int nextUnreadCount(int current, boolean incrementUnread) {
        return incrementUnread ? current + 1 : current;
    }

    /**
     * Truncates to the millisecond precision Cassandra's {@code timestamp} type actually stores.
     *
     * <p>Without this, the timestamp compared against the stored row (already truncated on the way
     * out of the database) would never match the one about to be written, so the delete in step 2
     * would fire even when the row is not moving — issuing a DELETE and an INSERT for the same
     * primary key. At equal write timestamps Cassandra resolves that tie in favour of the delete,
     * which would silently drop the row.
     */
    private static Instant toStoragePrecision(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS);
    }
}
