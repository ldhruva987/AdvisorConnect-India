package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.query.Criteria;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CassandraMessageRepository implements MessageRepository {

    private final SpringDataMessageRepository springDataRepo;
    private final CassandraOperations cassandraOps;

    @Override
    public Message save(Message message) {
        return springDataRepo.save(message);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The limit is applied with {@link Query#limit(long)} — i.e. a real CQL {@code LIMIT}. It
     * used to be passed as {@code CassandraPageRequest.first(limit)}, which sets the driver's
     * <em>fetch size</em>: {@code select} then transparently pages through every remaining page
     * and returns the entire partition. The bug was invisible in tests that only ever wrote a
     * handful of messages, and would have shipped whole conversation histories to any client that
     * asked for the last twenty.
     */
    @Override
    public List<Message> findByConversation(UUID userId, UUID advisorId, int limit) {
        return cassandraOps.select(conversation(userId, advisorId).limit(limit), Message.class);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Read-then-write, deliberately. Cassandra requires an {@code UPDATE} to restrict every
     * primary-key column, so there is no partition-wide {@code SET read = true WHERE user_id = ?
     * AND advisor_id = ?} to issue — unlike {@code DELETE}, {@code UPDATE} has no range form. The
     * rows are re-read, filtered in memory (a {@code read = false} predicate would need
     * {@code ALLOW FILTERING}, the column being unindexed), and written back as one batch. The
     * batch is single-partition, which is the one case where a Cassandra logged batch is both
     * atomic and cheap.
     */
    @Override
    public int markConversationRead(UUID userId, UUID advisorId) {
        List<Message> unread = cassandraOps.select(conversation(userId, advisorId), Message.class)
                .stream()
                .filter(message -> !message.isRead())
                .toList();

        if (unread.isEmpty()) {
            return 0;
        }
        unread.forEach(message -> message.setRead(true));
        cassandraOps.batchOps().update(unread).execute();
        return unread.size();
    }

    /** Restricts both partition-key components — the only way to address one conversation. */
    private static Query conversation(UUID userId, UUID advisorId) {
        return Query.query(
                Criteria.where("user_id").is(userId),
                Criteria.where("advisor_id").is(advisorId));
    }
}
