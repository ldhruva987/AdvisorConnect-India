package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import org.springframework.data.cassandra.repository.MapIdCassandraRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data access to {@code conversations_by_advisor}. See
 * {@link SpringDataConversationByUserRepository} for why the id type is {@code MapId}.
 */
interface SpringDataConversationByAdvisorRepository extends MapIdCassandraRepository<ConversationByAdvisor> {

    /** One advisor's whole inbox, already {@code last_message_at DESC} by clustering order. */
    List<ConversationByAdvisor> findByAdvisorId(UUID advisorId);
}
