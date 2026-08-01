package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.query.CassandraPageRequest;
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

    @Override
    public List<Message> findByConversation(UUID userId, UUID advisorId, int limit) {
        Query query = Query.query(
                Criteria.where("user_id").is(userId),
                Criteria.where("advisor_id").is(advisorId)
        ).pageRequest(CassandraPageRequest.first(limit));
        return cassandraOps.select(query, Message.class);
    }
}
