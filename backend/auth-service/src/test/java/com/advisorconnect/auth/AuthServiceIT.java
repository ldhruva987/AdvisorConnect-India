package com.advisorconnect.auth;

import com.advisorconnect.auth.domain.model.User;
import com.advisorconnect.auth.domain.model.UserRole;
import com.advisorconnect.auth.domain.port.out.UserRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
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
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end auth flows against a real Postgres, Kafka and Redis.
 *
 * <p>Run by Failsafe under {@code mvn verify} (requires Docker); {@code mvn test} skips
 * {@code *IT} classes entirely.
 *
 * <p>The two properties worth an integration test — rather than a mock — are that the persisted
 * role really is {@code USER} whatever the HTTP body asked for, and that a {@code user.registered}
 * message really reaches a broker. Both were previously only true by inspection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AuthServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withInitScript("init-auth-schema.sql");

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        // Seeding stays off: this suite asserts the request-driven paths, and an unexpected
        // administrator in the table would mask a privilege bug rather than reveal one.
        registry.add("admin.seed.email", () -> "");
        registry.add("admin.seed.password", () -> "");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    // ──────────────────────────────────────────────── registration is locked to USER

    @Test
    @DisplayName("POST /auth/register persists role=USER even when the body demands ADMIN")
    void registrationCannotSelfElevate() {
        String email = uniqueEmail("escalate");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/register",
                json("""
                        {"email":"%s","password":"password123","role":"ADMIN"}""".formatted(email)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("role", "USER");

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertThat(persisted.getRole()).isEqualTo(UserRole.USER);
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("registration publishes user.registered carrying the persisted id and email")
    void registrationPublishesEvent() {
        String email = uniqueEmail("published");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/auth/register",
                json("""
                        {"email":"%s","password":"password123"}""".formatted(email)),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();

        String payload = consumeFirstMatching("user.registered", userId.toString());
        assertThat(payload)
                .contains("USER_REGISTERED")
                .contains(userId.toString())
                .contains(email);
    }

    // ─────────────────────────────────────────────────────── admin provisioning

    @Test
    @DisplayName("POST /auth/admin/users is refused without an ADMIN identity and honoured with one")
    void adminProvisioning() {
        // Refused for an anonymous caller...
        ResponseEntity<String> anonymous = restTemplate.postForEntity(
                "/auth/admin/users",
                json("""
                        {"email":"%s","password":"password123"}""".formatted(uniqueEmail("nope"))),
                String.class);
        assertThat(anonymous.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);

        // ...and for an authenticated plain user...
        String adminEmail = uniqueEmail("admin");
        ResponseEntity<String> asUser = restTemplate.exchange(
                "/auth/admin/users",
                HttpMethod.POST,
                identified(UUID.randomUUID(), "USER", """
                        {"email":"%s","password":"password123"}""".formatted(adminEmail)),
                String.class);
        assertThat(asUser.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(userRepository.existsByEmail(adminEmail)).isFalse();

        // ...but honoured for an administrator.
        ResponseEntity<Map> asAdmin = restTemplate.exchange(
                "/auth/admin/users",
                HttpMethod.POST,
                identified(UUID.randomUUID(), "ADMIN", """
                        {"email":"%s","password":"password123"}""".formatted(adminEmail)),
                Map.class);
        assertThat(asAdmin.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(asAdmin.getBody()).containsKey("userId");
        // No session is handed back for the account just created.
        assertThat(asAdmin.getBody()).doesNotContainKeys("accessToken", "refreshToken");

        assertThat(userRepository.findByEmail(adminEmail).orElseThrow().getRole())
                .isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("a newly provisioned admin can log in and receives an ADMIN token")
    void provisionedAdminCanLogIn() {
        String adminEmail = uniqueEmail("loginadmin");

        restTemplate.exchange(
                "/auth/admin/users",
                HttpMethod.POST,
                identified(UUID.randomUUID(), "ADMIN", """
                        {"email":"%s","password":"password123"}""".formatted(adminEmail)),
                Map.class);

        ResponseEntity<Map> login = restTemplate.postForEntity(
                "/auth/login",
                json("""
                        {"email":"%s","password":"password123"}""".formatted(adminEmail)),
                Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).containsEntry("role", "ADMIN");
        assertThat(login.getBody()).containsKey("accessToken");
    }

    // ──────────────────────────────────────────────────── advisor role promotion

    @Test
    @DisplayName("an advisor.approved event promotes the account to ADVISOR")
    void advisorApprovalPromotesRole() {
        String email = uniqueEmail("advisor");
        restTemplate.postForEntity(
                "/auth/register",
                json("""
                        {"email":"%s","password":"password123"}""".formatted(email)),
                Map.class);

        UUID userId = userRepository.findByEmail(email).orElseThrow().getId();
        assertThat(userRepository.findById(userId).orElseThrow().getRole()).isEqualTo(UserRole.USER);

        kafkaTemplate.send("advisor.approved", userId.toString(), Map.of(
                "eventType", "ADVISOR_APPROVED",
                "advisorId", userId.toString(),
                "username", "some-advisor",
                "reviewedBy", UUID.randomUUID().toString()));

        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userRepository.findById(userId).orElseThrow().getRole())
                        .isEqualTo(UserRole.ADVISOR));
    }

    // ──────────────────────────────────────────────────────────────── helpers

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }

    private static HttpEntity<String> json(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** Mimics the identity headers the gateway injects after validating a JWT. */
    private static HttpEntity<String> identified(UUID userId, String role, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", role);
        return new HttpEntity<>(body, headers);
    }

    /**
     * Reads the topic from the beginning with a throwaway group and returns the first record whose
     * key matches. Values are read as raw strings so the assertion does not depend on the
     * producer's serializer configuration.
     */
    private String consumeFirstMatching(String topic, String key) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), "it-" + UUID.randomUUID(), "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {

            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No record on topic " + topic + " with key " + key);
    }
}
