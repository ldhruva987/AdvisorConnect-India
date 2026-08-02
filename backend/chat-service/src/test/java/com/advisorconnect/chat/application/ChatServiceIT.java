package com.advisorconnect.chat.application;

import com.advisorconnect.chat.adapter.out.persistence.CassandraConversationRepository;
import com.advisorconnect.chat.adapter.out.persistence.CassandraMessageRepository;
import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationByUser;
import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.cassandra.DataCassandraTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips a {@link Message} through a real Cassandra whose schema came from the real
 * {@code scripts/cassandra/init.cql}.
 *
 * <p>This is the regression test for the schema drift that motivated Phase 7: {@code init.cql}
 * declared the clustering column as {@code id} while {@code Message} mapped it as
 * {@code message_id}, and the {@code read} flag existed only on the entity. Nothing caught it,
 * because every other test either mocked the repository or let Spring Data generate the table
 * from the entity — which by construction can never disagree with the entity.
 *
 * <p>Two things therefore matter here and are deliberate:
 * <ul>
 *   <li>{@code schema-action=none} — Spring must not create the table. The schema under test is
 *       the one the operator actually applies in production.</li>
 *   <li>The CQL is loaded from the real file, placed on the test classpath by a {@code
 *       testResource} entry in {@code pom.xml} rather than copied. A copied fixture would drift
 *       in exactly the way this test exists to detect.</li>
 * </ul>
 *
 * <p>Requires a Docker daemon; run via {@code mvn verify}.
 */
@Testcontainers
@DataCassandraTest
@Import({CassandraMessageRepository.class, CassandraConversationRepository.class, ChatService.class})
class ChatServiceIT {

    /** Matches the image pinned in docker-compose.yml, so the schema is exercised on that version. */
    @Container
    static final CassandraContainer<?> CASSANDRA =
            new CassandraContainer<>(DockerImageName.parse("cassandra:4.1"))
                    .withEnv("HEAP_NEWSIZE", "128M")
                    .withEnv("MAX_HEAP_SIZE", "512M")
                    .withStartupTimeout(Duration.ofMinutes(5));

    /** The keyspace created by init.cql. */
    private static final String KEYSPACE = "chat_service";

    @DynamicPropertySource
    static void cassandraProperties(DynamicPropertyRegistry registry) {
        // Applied here rather than in @BeforeAll: the keyspace must exist before the context
        // refreshes, because Spring's CqlSession binds to it during startup.
        applyInitCql();

        registry.add("spring.cassandra.contact-points", CASSANDRA::getHost);
        registry.add("spring.cassandra.port", () -> CASSANDRA.getMappedPort(CassandraContainer.CQL_PORT));
        registry.add("spring.cassandra.local-datacenter", CASSANDRA::getLocalDatacenter);
        registry.add("spring.cassandra.keyspace-name", () -> KEYSPACE);
        // Non-negotiable: letting Spring generate the table would defeat the entire test.
        registry.add("spring.cassandra.schema-action", () -> "none");
    }

    @Autowired
    private ChatService chatService;

    @Autowired
    private CqlSession cqlSession;

    // ── the schema itself ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the deployed messages table has the columns Message.java maps, and no stale 'id'")
    void deployedSchemaMatchesTheEntityMapping() {
        TableMetadata messages = cqlSession.getMetadata()
                .getKeyspace(KEYSPACE).orElseThrow()
                .getTable("messages").orElseThrow();

        List<String> columns = messages.getColumns().values().stream()
                .map(c -> c.getName().asInternal())
                .toList();

        assertThat(columns).contains("user_id", "advisor_id", "created_at", "message_id",
                "sender_id", "sender_type", "text", "read");
        // The original defect: the clustering column was named `id`, which Message.java never mapped.
        assertThat(columns).doesNotContain("id");

        assertThat(messages.getPartitionKey().stream().map(c -> c.getName().asInternal()))
                .containsExactly("user_id", "advisor_id");
        assertThat(messages.getClusteringColumns().keySet().stream().map(ColumnMetadata::getName)
                .map(name -> name.asInternal()))
                .containsExactly("created_at", "message_id");
    }

    // ── round-trip through the real schema ───────────────────────────────────────────────

    @Test
    @DisplayName("a message written through ChatService reads back with every field intact")
    void messageRoundTrips() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        Message saved = chatService.sendMessage(userId, advisorId, "how do I rebalance?", "user");

        List<Message> found = chatService.getMessages(userId, advisorId, 10);

        assertThat(found).hasSize(1);
        Message read = found.get(0);
        assertThat(read.getUserId()).isEqualTo(userId);
        assertThat(read.getAdvisorId()).isEqualTo(advisorId);
        assertThat(read.getMessageId()).isEqualTo(saved.getMessageId());
        assertThat(read.getSenderId()).isEqualTo(userId);
        assertThat(read.getSenderType()).isEqualTo("user");
        assertThat(read.getText()).isEqualTo("how do I rebalance?");
        assertThat(read.isRead()).isFalse();
        // Cassandra's timestamp type is millisecond-precision; Instant.now() is finer.
        assertThat(read.getCreatedAt())
                .isEqualTo(saved.getCreatedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("an advisor-sent message is attributed to the advisor")
    void advisorSenderIsPersisted() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "rebalance quarterly", "advisor");

        Message read = chatService.getMessages(userId, advisorId, 10).get(0);
        assertThat(read.getSenderType()).isEqualTo("advisor");
        assertThat(read.getSenderId()).isEqualTo(advisorId);
    }

    @Test
    @DisplayName("a conversation reads back newest-first, per the DESC clustering order")
    void conversationIsOrderedNewestFirst() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "first", "user");
        Thread.sleep(5);   // distinct millisecond timestamps; created_at is the clustering key
        chatService.sendMessage(userId, advisorId, "second", "advisor");
        Thread.sleep(5);
        chatService.sendMessage(userId, advisorId, "third", "user");

        assertThat(chatService.getMessages(userId, advisorId, 10))
                .extracting(Message::getText)
                .containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("conversations are isolated by their (user, advisor) partition")
    void conversationsAreIsolated() {
        UUID userId = UUID.randomUUID();
        UUID advisorA = UUID.randomUUID();
        UUID advisorB = UUID.randomUUID();

        chatService.sendMessage(userId, advisorA, "to A", "user");
        chatService.sendMessage(userId, advisorB, "to B", "user");

        assertThat(chatService.getMessages(userId, advisorA, 10))
                .extracting(Message::getText).containsExactly("to A");
        assertThat(chatService.getMessages(userId, advisorB, 10))
                .extracting(Message::getText).containsExactly("to B");
    }

    @Test
    @DisplayName("an empty conversation reads back as an empty list, not an error")
    void unknownConversationIsEmpty() {
        assertThat(chatService.getMessages(UUID.randomUUID(), UUID.randomUUID(), 10)).isEmpty();
    }

    @Test
    @DisplayName("the 'read' flag persists as true, proving the column is really mapped")
    void readFlagIsWritable() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        UUID messageId = UUID.randomUUID();

        cqlSession.execute(
                "INSERT INTO messages (user_id, advisor_id, created_at, message_id, sender_id, "
                        + "sender_type, text, read) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, advisorId, createdAt, messageId, userId, "user", "already seen", true);

        Message read = chatService.getMessages(userId, advisorId, 10).get(0);
        assertThat(read.isRead()).isTrue();
        assertThat(read.getMessageId()).isEqualTo(messageId);
    }

    // ── the limit regression ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a limit really caps the rows returned, rather than setting a driver page size")
    void limitCapsTheRowsReturned() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        // Comfortably more than one 5-row page, and more than the default fetch size would have
        // paged in one go either.
        for (int i = 0; i < 25; i++) {
            chatService.sendMessage(userId, advisorId, "message " + i, "user");
        }

        // The original defect: the limit was passed as CassandraPageRequest.first(limit), which
        // sets the driver's *fetch size*. select() then transparently paged through the rest and
        // returned the whole partition, so this asserted 5 and got 25. Every earlier test wrote
        // only a handful of messages, so nothing ever noticed.
        assertThat(chatService.getMessages(userId, advisorId, 5)).hasSize(5);
        assertThat(chatService.getMessages(userId, advisorId, 10)).hasSize(10);

        // A limit above the row count is not an error and does not truncate.
        assertThat(chatService.getMessages(userId, advisorId, 100)).hasSize(25);
    }

    @Test
    @DisplayName("a limited read still returns the newest messages, not an arbitrary slice")
    void limitKeepsTheNewestMessages() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        for (int i = 0; i < 10; i++) {
            chatService.sendMessage(userId, advisorId, "message " + i, "user");
            sleepAMoment();   // distinct millisecond timestamps; created_at is the clustering key
        }

        assertThat(chatService.getMessages(userId, advisorId, 3))
                .extracting(Message::getText)
                .containsExactly("message 9", "message 8", "message 7");
    }

    // ── the conversation-summary read model ──────────────────────────────────────────────

    @Test
    @DisplayName("the deployed conversation tables have the columns the entities map")
    void deployedSummarySchemaMatchesTheEntityMappings() {
        TableMetadata byUser = table("conversations_by_user");
        assertThat(columnNames(byUser)).contains("user_id", "advisor_id", "advisor_username",
                "advisor_color", "last_message", "last_message_at", "unread_count",
                "is_advisor_online");
        assertThat(byUser.getPartitionKey().stream().map(c -> c.getName().asInternal()))
                .containsExactly("user_id");
        assertThat(clusteringColumnNames(byUser)).containsExactly("last_message_at", "advisor_id");

        TableMetadata byAdvisor = table("conversations_by_advisor");
        assertThat(columnNames(byAdvisor)).contains("advisor_id", "user_id", "last_message",
                "last_message_at", "unread_count");
        assertThat(byAdvisor.getPartitionKey().stream().map(c -> c.getName().asInternal()))
                .containsExactly("advisor_id");
        assertThat(clusteringColumnNames(byAdvisor)).containsExactly("last_message_at", "user_id");
    }

    @Test
    @DisplayName("a sent message appears in both inboxes, unread only for the recipient")
    void sendingUpdatesBothInboxesWithAsymmetricUnread() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "is Thursday okay?", "user");

        ConversationByUser seekerRow = onlyUserConversation(userId);
        assertThat(seekerRow.getAdvisorId()).isEqualTo(advisorId);
        assertThat(seekerRow.getLastMessage()).isEqualTo("is Thursday okay?");
        // The sender does not earn a badge for their own message.
        assertThat(seekerRow.getUnreadCount()).isZero();

        ConversationByAdvisor advisorRow = onlyAdvisorConversation(advisorId);
        assertThat(advisorRow.getUserId()).isEqualTo(userId);
        assertThat(advisorRow.getLastMessage()).isEqualTo("is Thursday okay?");
        // The recipient does.
        assertThat(advisorRow.getUnreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("each side's badge counts only what the other side sent")
    void unreadCountsAccumulatePerRecipient() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "first", "user");
        sleepAMoment();
        chatService.sendMessage(userId, advisorId, "second", "user");
        sleepAMoment();
        chatService.sendMessage(userId, advisorId, "reply", "advisor");

        // The seeker was sent one message and wrote two.
        assertThat(onlyUserConversation(userId).getUnreadCount()).isEqualTo(1);
        // The advisor was sent two and wrote one.
        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isEqualTo(2);

        // Both previews show the latest message regardless of who wrote it.
        assertThat(onlyUserConversation(userId).getLastMessage()).isEqualTo("reply");
        assertThat(onlyAdvisorConversation(advisorId).getLastMessage()).isEqualTo("reply");
    }

    @Test
    @DisplayName("repeated messages relocate the inbox row instead of accumulating stale copies")
    void inboxKeepsExactlyOneRowPerConversation() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        for (int i = 0; i < 5; i++) {
            chatService.sendMessage(userId, advisorId, "message " + i, "user");
            sleepAMoment();   // force a distinct last_message_at, so the row really does move
        }

        // last_message_at is a clustering column, so advancing it changes the row's identity.
        // Without the delete-then-insert in CassandraConversationRepository this would be 5 rows
        // and the inbox would list the same conversation five times.
        assertThat(chatService.getUserConversations(userId)).hasSize(1);
        assertThat(chatService.getAdvisorConversations(advisorId)).hasSize(1);
        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("an inbox lists its conversations most-recently-active first")
    void inboxIsOrderedByRecency() {
        UUID userId = UUID.randomUUID();
        UUID advisorA = UUID.randomUUID();
        UUID advisorB = UUID.randomUUID();

        chatService.sendMessage(userId, advisorA, "to A", "user");
        sleepAMoment();
        chatService.sendMessage(userId, advisorB, "to B", "user");

        assertThat(chatService.getUserConversations(userId))
                .extracting(ConversationByUser::getAdvisorId)
                .containsExactly(advisorB, advisorA);

        sleepAMoment();
        chatService.sendMessage(userId, advisorA, "to A again", "user");

        // A moves back to the top now that it is the most recent.
        assertThat(chatService.getUserConversations(userId))
                .extracting(ConversationByUser::getAdvisorId)
                .containsExactly(advisorA, advisorB);
    }

    @Test
    @DisplayName("an inbox is scoped to its owner")
    void inboxesAreScopedToTheirOwner() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();
        chatService.sendMessage(userId, advisorId, "hello", "user");

        assertThat(chatService.getUserConversations(UUID.randomUUID())).isEmpty();
        assertThat(chatService.getAdvisorConversations(UUID.randomUUID())).isEmpty();
    }

    // ── mark-as-read ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("marking read flips the messages and clears only the caller's badge")
    void markingReadClearsOnlyTheCallersBadge() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "first", "user");
        sleepAMoment();
        chatService.sendMessage(userId, advisorId, "second", "user");
        sleepAMoment();
        chatService.sendMessage(userId, advisorId, "reply", "advisor");

        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isEqualTo(2);
        assertThat(onlyUserConversation(userId).getUnreadCount()).isEqualTo(1);

        int marked = chatService.markConversationRead(
                ConversationParticipants.resolve(advisorId, ConversationParticipants.ROLE_ADVISOR, userId));

        // All three messages share one `read` flag — the schema has no per-side flag — so the
        // advisor's own message is flipped too.
        assertThat(marked).isEqualTo(3);
        assertThat(chatService.getMessages(userId, advisorId, 10)).allMatch(Message::isRead);

        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isZero();
        // The seeker still has their own unread reply — clearing it would claim they had read it.
        assertThat(onlyUserConversation(userId).getUnreadCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a seeker marking read clears the seeker's badge, mirroring the advisor case")
    void seekerCanMarkReadToo() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "unprompted advice", "advisor");
        assertThat(onlyUserConversation(userId).getUnreadCount()).isEqualTo(1);

        int marked = chatService.markConversationRead(
                ConversationParticipants.resolve(userId, ConversationParticipants.ROLE_USER, advisorId));

        assertThat(marked).isEqualTo(1);
        assertThat(onlyUserConversation(userId).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("marking read is idempotent — the second call reports nothing left to do")
    void markingReadIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();
        chatService.sendMessage(userId, advisorId, "hello", "user");

        ConversationParticipants advisor = ConversationParticipants.resolve(
                advisorId, ConversationParticipants.ROLE_ADVISOR, userId);

        assertThat(chatService.markConversationRead(advisor)).isEqualTo(1);
        assertThat(chatService.markConversationRead(advisor)).isZero();
        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isZero();
    }

    @Test
    @DisplayName("marking an empty conversation read is a no-op, not an error")
    void markingAnEmptyConversationReadIsHarmless() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        int marked = chatService.markConversationRead(
                ConversationParticipants.resolve(userId, ConversationParticipants.ROLE_USER, advisorId));

        assertThat(marked).isZero();
        assertThat(chatService.getUserConversations(userId)).isEmpty();
    }

    @Test
    @DisplayName("a new message after marking read raises the badge again from zero")
    void badgeResumesAfterBeingCleared() {
        UUID userId = UUID.randomUUID();
        UUID advisorId = UUID.randomUUID();

        chatService.sendMessage(userId, advisorId, "first", "user");
        chatService.markConversationRead(ConversationParticipants.resolve(
                advisorId, ConversationParticipants.ROLE_ADVISOR, userId));
        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isZero();

        sleepAMoment();
        chatService.sendMessage(userId, advisorId, "second", "user");

        assertThat(onlyAdvisorConversation(advisorId).getUnreadCount()).isEqualTo(1);
        assertThat(onlyAdvisorConversation(advisorId).getLastMessage()).isEqualTo("second");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────

    private ConversationByUser onlyUserConversation(UUID userId) {
        List<ConversationByUser> conversations = chatService.getUserConversations(userId);
        assertThat(conversations).hasSize(1);
        return conversations.get(0);
    }

    private ConversationByAdvisor onlyAdvisorConversation(UUID advisorId) {
        List<ConversationByAdvisor> conversations = chatService.getAdvisorConversations(advisorId);
        assertThat(conversations).hasSize(1);
        return conversations.get(0);
    }

    private TableMetadata table(String name) {
        return cqlSession.getMetadata().getKeyspace(KEYSPACE).orElseThrow()
                .getTable(name).orElseThrow();
    }

    private static List<String> columnNames(TableMetadata table) {
        return table.getColumns().values().stream().map(c -> c.getName().asInternal()).toList();
    }

    private static List<String> clusteringColumnNames(TableMetadata table) {
        return table.getClusteringColumns().keySet().stream()
                .map(ColumnMetadata::getName)
                .map(name -> name.asInternal())
                .toList();
    }

    /**
     * Advances the wall clock past Cassandra's millisecond timestamp resolution.
     *
     * <p>Needed wherever a test depends on rows having <em>distinct</em> {@code last_message_at}
     * or {@code created_at} values: both are primary-key components, so two writes inside the same
     * millisecond collapse onto the same key instead of producing two orderable rows.
     */
    private static void sleepAMoment() {
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted between writes", e);
        }
    }

    // ── schema bootstrap ─────────────────────────────────────────────────────────────────

    /** Executes the production init.cql against the container, statement by statement. */
    private static void applyInitCql() {
        try (CqlSession session = openAdminSession()) {
            statements(readInitCql()).forEach(session::execute);
        }
    }

    /**
     * The container's port-listening wait strategy can win the race against Cassandra finishing
     * its own startup, so connection attempts are retried briefly rather than failing the build.
     */
    private static CqlSession openAdminSession() {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                return CqlSession.builder()
                        .addContactPoint(new InetSocketAddress(
                                CASSANDRA.getHost(), CASSANDRA.getMappedPort(CassandraContainer.CQL_PORT)))
                        .withLocalDatacenter(CASSANDRA.getLocalDatacenter())
                        .build();
            } catch (RuntimeException e) {
                lastFailure = e;
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted waiting for Cassandra", interrupted);
                }
            }
        }
        throw new IllegalStateException("Cassandra never became reachable", lastFailure);
    }

    private static String readInitCql() {
        // Placed at the classpath root by the testResource entry in pom.xml — the real file,
        // not a copy, so schema drift surfaces here.
        try (InputStream in = ChatServiceIT.class.getResourceAsStream("/init.cql")) {
            if (in == null) {
                throw new IllegalStateException(
                        "init.cql is not on the test classpath — check the testResources entry in chat-service/pom.xml");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read init.cql", e);
        }
    }

    /**
     * Splits a CQL script into executable statements. Line comments are stripped first so that
     * the trailing comment-only fragment after the last {@code ;} does not become a statement.
     */
    private static List<String> statements(String script) {
        String withoutComments = script.lines()
                .map(line -> {
                    int comment = line.indexOf("--");
                    return comment >= 0 ? line.substring(0, comment) : line;
                })
                .collect(Collectors.joining("\n"));

        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
