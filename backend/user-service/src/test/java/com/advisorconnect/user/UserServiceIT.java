package com.advisorconnect.user;

import com.advisorconnect.user.domain.model.UserProfile;
import com.advisorconnect.user.domain.port.out.UserProfileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The direct regression test for the defect Phase 2 exists to fix: <strong>{@code GET /users/me}
 * 404'd for every real user</strong>, because auth-service owned registration, user-service owned
 * profiles, and nothing ever created the {@code user_profiles} row in between.
 *
 * <p>Exercised against a real Postgres and a real Kafka broker so the event contract itself is
 * under test, not a mock of it. Run by Failsafe under {@code mvn verify} (requires Docker);
 * {@code mvn test} skips {@code *IT} classes entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserServiceIT {

    private static final String TOPIC = "user.registered";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // The event is published as a JSON string; the consumer's JsonDeserializer is configured
        // to ignore type headers and materialise a HashMap, so no producer-side type info is
        // needed — which is also what makes the contract survive across services.
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ──────────────────────────────────────────────────────── the core regression

    @Test
    @DisplayName("GET /users/me returns 200 for a registered user, not the historical 404")
    void profileIsProvisionedFromRegistrationEvent() {
        UUID userId = UUID.randomUUID();
        String email = "alice-" + userId + "@example.com";

        // Before the event there is genuinely nothing — the old bug was that it stayed this way.
        assertThat(getMe(userId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        publishRegistered(userId, email);

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getMe(userId).getStatusCode()).isEqualTo(HttpStatus.OK));

        UserProfile profile = userProfileRepository.findById(userId).orElseThrow();
        assertThat(profile.getEmail()).isEqualTo(email);
        assertThat(profile.getUsername()).startsWith("alice");
    }

    @Test
    @DisplayName("the generated username comes from the email local part, never the domain")
    void usernameDerivedFromLocalPart() {
        UUID userId = UUID.randomUUID();
        publishRegistered(userId, "Bob.Smith+tag@example.com");

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userProfileRepository.findById(userId)).isPresent());

        assertThat(userProfileRepository.findById(userId).orElseThrow().getUsername())
                .isEqualTo("bobsmithtag");
    }

    @Test
    @DisplayName("two users with the same email local part get distinct usernames")
    void collidingUsernamesAreSuffixed() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        publishRegistered(first, "duplicate@example.com");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userProfileRepository.findById(first)).isPresent());

        publishRegistered(second, "duplicate@other-domain.com");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userProfileRepository.findById(second)).isPresent());

        String firstUsername = userProfileRepository.findById(first).orElseThrow().getUsername();
        String secondUsername = userProfileRepository.findById(second).orElseThrow().getUsername();

        assertThat(firstUsername).isEqualTo("duplicate");
        assertThat(secondUsername).isEqualTo("duplicate2");
    }

    // ────────────────────────────────────────────────────────────── idempotency

    /**
     * Kafka is at-least-once and the consumer reads from {@code earliest}, so a redelivery is
     * routine rather than exceptional. The edit made in between is the point: a second delivery
     * must not reset the profile to its freshly-registered state.
     */
    @Test
    @DisplayName("a redelivered registration event does not overwrite edits the user has made")
    void redeliveryPreservesUserEdits() {
        UUID userId = UUID.randomUUID();
        String email = "carol-" + userId + "@example.com";

        publishRegistered(userId, email);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getMe(userId).getStatusCode()).isEqualTo(HttpStatus.OK));

        String originalUsername = userProfileRepository.findById(userId).orElseThrow().getUsername();

        ResponseEntity<Map> updated = restTemplate.exchange(
                "/users/me",
                HttpMethod.PUT,
                identified(userId, """
                        {"displayName":"Carol C.","bio":"Hello there"}"""),
                Map.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        publishRegistered(userId, email);

        // Nothing to wait for on a no-op, so give the consumer a window to get it wrong.
        await().during(3, TimeUnit.SECONDS).atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            UserProfile profile = userProfileRepository.findById(userId).orElseThrow();
            assertThat(profile.getDisplayName()).isEqualTo("Carol C.");
            assertThat(profile.getBio()).isEqualTo("Hello there");
            assertThat(profile.getUsername()).isEqualTo(originalUsername);
        });
    }

    // ───────────────────────────────────────────────────── endpoint contracts

    @Test
    @DisplayName("PUT /users/me actually persists — the untransacted version silently discarded it")
    void updateIsPersistedAcrossRequests() {
        UUID userId = UUID.randomUUID();
        publishRegistered(userId, "dave-" + userId + "@example.com");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(getMe(userId).getStatusCode()).isEqualTo(HttpStatus.OK));

        restTemplate.exchange(
                "/users/me",
                HttpMethod.PUT,
                identified(userId, """
                        {"displayName":"Dave D.","bio":"Persisted?"}"""),
                Map.class);

        // A *separate* request is what matters: the old code mutated a managed entity outside any
        // transaction, so the change never reached the database and vanished by the next read.
        ResponseEntity<Map> reread = getMe(userId);
        assertThat(reread.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reread.getBody()).containsEntry("displayName", "Dave D.");
        assertThat(reread.getBody()).containsEntry("bio", "Persisted?");
    }

    @Test
    @DisplayName("GET /users/{id} serves the public profile and 404s for an unknown id")
    void publicProfileLookup() {
        UUID userId = UUID.randomUUID();
        publishRegistered(userId, "erin-" + userId + "@example.com");
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userProfileRepository.findById(userId)).isPresent());

        ResponseEntity<Map> found = restTemplate.getForEntity("/users/{id}", Map.class, userId);
        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).containsEntry("id", userId.toString());

        ResponseEntity<Map> missing =
                restTemplate.getForEntity("/users/{id}", Map.class, UUID.randomUUID());
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /users/me without identity headers is refused, not answered")
    void meRequiresIdentity() {
        ResponseEntity<String> response = restTemplate.getForEntity("/users/me", String.class);
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a malformed registration event is discarded without creating a profile")
    void malformedEventIsDiscarded() {
        long before = countProfiles();

        kafkaTemplate.send(TOPIC, "bad", """
                {"eventType":"USER_REGISTERED","userId":"not-a-uuid","email":"x@example.com"}""");

        await().during(3, TimeUnit.SECONDS).atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(countProfiles()).isEqualTo(before));
    }

    // ──────────────────────────────────────────────────────────────── helpers

    /** Byte-for-byte the payload auth-service's {@code AuthEventPublisher} produces. */
    private void publishRegistered(UUID userId, String email) {
        kafkaTemplate.send(TOPIC, userId.toString(), """
                {"eventType":"USER_REGISTERED","userId":"%s","email":"%s"}"""
                .formatted(userId, email));
    }

    private ResponseEntity<Map> getMe(UUID userId) {
        return restTemplate.exchange(
                "/users/me", HttpMethod.GET, identified(userId, null), Map.class);
    }

    /** Mimics the identity headers the gateway injects after validating a JWT. */
    private static HttpEntity<String> identified(UUID userId, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "USER");
        return new HttpEntity<>(body, headers);
    }

    /** Straight to SQL: the port has no count(), and adding one purely for a test would be tail-wagging. */
    private long countProfiles() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM user_profiles", Long.class);
        return count == null ? 0 : count;
    }
}
