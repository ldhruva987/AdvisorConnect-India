package com.advisorconnect.chat.domain.model;

import lombok.*;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An advisor's inbox row — the mirror image of {@link ConversationByUser}.
 *
 * <p>Maps {@code conversations_by_advisor} from {@code scripts/cassandra/init.cql}:
 *
 * <pre>{@code
 * PRIMARY KEY (advisor_id, last_message_at, user_id)
 * WITH CLUSTERING ORDER BY (last_message_at DESC, user_id ASC)
 * }</pre>
 *
 * <p>The same clustering-key caveat applies: {@code last_message_at} is part of the primary key,
 * so advancing it is delete-then-insert rather than an in-place update.
 *
 * <p>This table deliberately carries no display columns — the CQL gives it only
 * {@code last_message}, {@code last_message_at} and {@code unread_count}. The advisor's own
 * username/colour would be redundant here, and the counterparty user's are not modelled at all.
 */
@Table("conversations_by_advisor")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConversationByAdvisor {

    @PrimaryKeyColumn(name = "advisor_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID advisorId;

    @PrimaryKeyColumn(name = "last_message_at", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING)
    private Instant lastMessageAt;

    @PrimaryKeyColumn(name = "user_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private UUID userId;

    @Column("last_message")
    private String lastMessage;

    @Column("unread_count")
    private int unreadCount;
}
