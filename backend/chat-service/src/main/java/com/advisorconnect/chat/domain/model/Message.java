package com.advisorconnect.chat.domain.model;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Chat message stored in Cassandra.
 *
 * Partition key: (user_id, advisor_id) co-locates all messages for a
 * conversation on the same Cassandra node for O(1) reads.
 * Clustering by created_at DESC enables time-ordered, paginated retrieval.
 */
@Table("messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @PrimaryKeyColumn(name = "user_id", type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "advisor_id", type = PrimaryKeyType.PARTITIONED)
    private UUID advisorId;

    @PrimaryKeyColumn(name = "created_at", type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private Instant createdAt;

    @PrimaryKeyColumn(name = "message_id", type = PrimaryKeyType.CLUSTERED)
    private UUID messageId;

    @Column("sender_id")
    private UUID senderId;

    @Column("sender_type")
    private String senderType; // "user" | "advisor"

    @Column("text")
    private String text;

    @Column("read")
    private boolean read = false;
}
