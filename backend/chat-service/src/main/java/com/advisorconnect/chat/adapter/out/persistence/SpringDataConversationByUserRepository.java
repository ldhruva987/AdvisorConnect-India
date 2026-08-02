package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.ConversationByUser;
import org.springframework.data.cassandra.repository.MapIdCassandraRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data access to {@code conversations_by_user}.
 *
 * <p>{@link MapIdCassandraRepository} rather than {@code CassandraRepository<T, UUID>}: the
 * entity's primary key is three flat {@code @PrimaryKeyColumn}s, not a single field, so its id
 * type is a {@code MapId}. Declaring {@code UUID} would compile and then fail at runtime on any
 * id-based operation.
 *
 * <p>Package-private — the domain talks to
 * {@link com.advisorconnect.chat.domain.port.out.ConversationRepository}, never to Spring Data.
 */
interface SpringDataConversationByUserRepository extends MapIdCassandraRepository<ConversationByUser> {

    /**
     * One seeker's whole inbox. This is a single-partition query, and Cassandra returns it in
     * clustering order — {@code last_message_at DESC} — so it needs no sorting.
     */
    List<ConversationByUser> findByUserId(UUID userId);
}
