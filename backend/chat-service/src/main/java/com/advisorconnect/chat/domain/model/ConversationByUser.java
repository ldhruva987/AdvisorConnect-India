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
 * A seeker's inbox row: one conversation as it appears in <em>their</em> conversation list.
 *
 * <p>Denormalised read model over {@code conversations_by_user}, whose declaration in
 * {@code scripts/cassandra/init.cql} is the source of truth for every mapping below:
 *
 * <pre>{@code
 * PRIMARY KEY (user_id, last_message_at, advisor_id)
 * WITH CLUSTERING ORDER BY (last_message_at DESC, advisor_id ASC)
 * }</pre>
 *
 * <p>Two consequences of that key shape drive how {@code CassandraConversationRepository} writes:
 * <ul>
 *   <li>The partition is the user, so one query returns a whole inbox already sorted
 *       most-recent-first — no ordering work at read time.</li>
 *   <li>{@code last_message_at} is a <em>clustering column</em>, i.e. part of the primary key.
 *       "Updating" it is therefore a delete of the old row plus an insert of a new one, not an
 *       {@code UPDATE}. Writing without the delete would leave one stale row per message ever
 *       sent.</li>
 * </ul>
 *
 * <p>{@code advisorUsername}/{@code advisorColor}/{@code advisorOnline} are display fields owned by
 * other services. Chat-service has no way to learn them on the message path, so it never invents
 * them: an upsert preserves whatever is already stored and leaves them unset otherwise.
 */
@Table("conversations_by_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ConversationByUser {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private UUID userId;

    @PrimaryKeyColumn(name = "last_message_at", ordinal = 1, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.DESCENDING)
    private Instant lastMessageAt;

    @PrimaryKeyColumn(name = "advisor_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED,
            ordering = Ordering.ASCENDING)
    private UUID advisorId;

    @Column("advisor_username")
    private String advisorUsername;

    @Column("advisor_color")
    private String advisorColor;

    @Column("last_message")
    private String lastMessage;

    @Column("unread_count")
    private int unreadCount;

    @Column("is_advisor_online")
    private boolean advisorOnline;
}
