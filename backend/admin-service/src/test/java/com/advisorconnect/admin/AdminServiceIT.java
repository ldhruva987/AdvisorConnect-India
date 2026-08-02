package com.advisorconnect.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard aggregation, end to end: real Postgres, real Kafka broker, real listeners, real
 * security filter chain.
 *
 * <p>What this is here to prove is that the counters survive the journey the unit tests cannot
 * cover — serialisation across the wire, the JSON payload shapes the other services actually
 * publish, the atomic UPDATE statements against a real database, and the lazy creation of the
 * singleton counters row on the very first event.
 *
 * <p>Counters are global singleton state shared by every test in this class, so the aggregation
 * story is deliberately one test rather than several that would race each other for the row.
 *
 * <p>Run by Failsafe under {@code mvn verify}; requires Docker. {@code mvn test} skips it.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AdminServiceIT {

    private static final int REGISTRATIONS = 3;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void wireContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // The service ships ddl-auto: validate against a migrated database; the container starts
        // empty, so the schema — audit_logs and the new platform_counters — comes from the
        // entities for the test run.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Events go on the wire as JSON text, exactly as the consumers must accept them. The
        // listeners ignore type headers (see application.yml), so no producer-side type
        // information is involved in making this work.
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID reviewingAdmin = UUID.randomUUID();

    @Test
    @DisplayName("a realistic run of platform events adds up to the dashboard figures")
    void eventsAggregateIntoTheDashboard() {
        List<UUID> registered = new ArrayList<>();
        for (int i = 0; i < REGISTRATIONS; i++) {
            UUID userId = UUID.randomUUID();
            registered.add(userId);
            publishUserRegistered(userId, "user-" + userId + "@example.com");
        }

        // Two people apply; one is approved, one is rejected. Pending should settle back to zero.
        UUID approvedApplication = UUID.randomUUID();
        UUID rejectedApplication = UUID.randomUUID();
        publishApplicationSubmitted(approvedApplication, registered.get(0), "ada");
        publishApplicationSubmitted(rejectedApplication, registered.get(1), "grace");

        UUID advisorId = UUID.randomUUID();
        publishAdvisorApproved(advisorId, "ada", reviewingAdmin);
        publishAdvisorRejected(rejectedApplication, "Insufficient experience", reviewingAdmin);

        // A finished session carrying its charge, and one in the shape booking-service publishes
        // today — no amount at all. Only the first may move revenue.
        publishBookingCompleted(UUID.randomUUID(), "90.00");
        publishBookingCompleted(UUID.randomUUID(), null);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            JsonNode stats = stats();
            assertThat(stats.get("totalUsers").asLong()).isEqualTo(REGISTRATIONS);
            assertThat(stats.get("approvedAdvisors").asLong()).isEqualTo(1);
            assertThat(stats.get("pendingApplications").asLong()).isZero();
            assertThat(new BigDecimal(stats.get("platformRevenue").asText()))
                    .as("the amount-less booking.completed must not be counted as revenue")
                    .isEqualByComparingTo(new BigDecimal("90.00"));
            // One entry per advisor lifecycle event: 2 submitted, 1 approved, 1 rejected.
            assertThat(stats.get("totalAdminActions").asLong()).isEqualTo(4);
        });

        assertAuditTrailRecordedTheDecisions();
    }

    /**
     * The decisions are in the trail with the reviewing administrator attached — the point of
     * consuming these events rather than trusting the admin UI to post an audit entry.
     */
    private void assertAuditTrailRecordedTheDecisions() {
        JsonNode logs = auditLogs();

        JsonNode approval = entryWithAction(logs, "ADVISOR_APPROVED");
        assertThat(approval.get("adminId").asText()).isEqualTo(reviewingAdmin.toString());

        JsonNode rejection = entryWithAction(logs, "ADVISOR_REJECTED");
        assertThat(rejection.get("adminId").asText()).isEqualTo(reviewingAdmin.toString());
        assertThat(rejection.get("note").asText()).contains("Insufficient experience");

        JsonNode submission = entryWithAction(logs, "ADVISOR_APPLICATION_SUBMITTED");
        assertThat(submission.get("adminId").asText())
                .as("nobody administered anything; the applicant must not be recorded as an admin")
                .isEqualTo(new UUID(0L, 0L).toString());
        assertThat(submission.get("targetType").asText()).isEqualTo("AdvisorApplication");
    }

    @Test
    @DisplayName("the dashboard is not readable without the ADMIN role")
    void statsRequireTheAdminRole() throws Exception {
        mockMvc.perform(get("/admin/stats")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());

        // No identity headers at all — the gateway was bypassed entirely. No authentication
        // mechanism is configured, so Spring Security answers 403 rather than 401; the property
        // under test is that the request does not succeed.
        mockMvc.perform(get("/admin/stats"))
                .andExpect(status().is(org.hamcrest.Matchers.isOneOf(401, 403)));
    }

    // ------------------------------------------------------------------------------ HTTP

    private JsonNode stats() throws Exception {
        return objectMapper.readTree(asAdmin("/admin/stats"));
    }

    private JsonNode auditLogs() {
        try {
            return objectMapper.readTree(asAdmin("/admin/audit-logs?page=0&size=50"));
        } catch (Exception e) {
            throw new IllegalStateException("could not read the audit trail", e);
        }
    }

    private String asAdmin(String path) throws Exception {
        return mockMvc.perform(get(path)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static JsonNode entryWithAction(JsonNode logs, String action) {
        for (JsonNode entry : logs) {
            if (action.equals(entry.get("action").asText())) {
                return entry;
            }
        }
        throw new AssertionError("no audit entry with action " + action + " in " + logs);
    }

    // ---------------------------------------------------------------------------- events
    // Payloads are byte-for-byte what AuthEventPublisher, AdvisorEventPublisher and
    // BookingEventPublisher put on these topics — including the email fields those publishers
    // carry for notification-service and admin-service has no use for. They are here precisely
    // because the listeners must ignore fields they do not recognise: a downstream service adding
    // a key to a shared topic must not be able to break this one.

    private void publishUserRegistered(UUID userId, String email) {
        send("user.registered", userId, """
                {"eventType":"USER_REGISTERED","userId":"%s","email":"%s"}"""
                .formatted(userId, email));
    }

    private void publishApplicationSubmitted(UUID applicationId, UUID userId, String username) {
        send("advisor.application.submitted", applicationId, """
                {"eventType":"ADVISOR_APPLICATION_SUBMITTED","applicationId":"%s",\
                "userId":"%s","username":"%s"}"""
                .formatted(applicationId, userId, username));
    }

    private void publishAdvisorApproved(UUID advisorId, String username, UUID reviewedBy) {
        send("advisor.approved", advisorId, """
                {"eventType":"ADVISOR_APPROVED","advisorId":"%s","username":"%s",\
                "reviewedBy":"%s","email":"%s@example.com"}"""
                .formatted(advisorId, username, reviewedBy, username));
    }

    private void publishAdvisorRejected(UUID applicationId, String reason, UUID reviewedBy) {
        send("advisor.rejected", applicationId, """
                {"eventType":"ADVISOR_REJECTED","applicationId":"%s","reason":"%s",\
                "reviewedBy":"%s","email":"rejected@example.com"}"""
                .formatted(applicationId, reason, reviewedBy));
    }

    /**
     * @param amountCharged {@code null} reproduces today's payload, which carries no amount at
     *                      all; a value reproduces the payload once booking-service includes one.
     */
    private void publishBookingCompleted(UUID bookingId, String amountCharged) {
        String amount = amountCharged == null ? "" : ",\"amountCharged\":\"%s\"".formatted(amountCharged);
        send("booking.completed", bookingId, """
                {"eventType":"BOOKING_COMPLETED","bookingId":"%s","userId":"%s","advisorId":"%s",\
                "userEmail":"attendee@example.com"%s}"""
                .formatted(bookingId, UUID.randomUUID(), UUID.randomUUID(), amount));
    }

    private void send(String topic, UUID key, String payload) {
        kafkaTemplate.send(topic, key.toString(), payload);
    }
}
