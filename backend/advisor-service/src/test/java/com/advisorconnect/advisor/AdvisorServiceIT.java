package com.advisorconnect.advisor;

import com.advisorconnect.advisor.adapter.in.web.dto.AdvisorPublicDto;
import com.advisorconnect.advisor.domain.model.AdvisorApplication;
import com.advisorconnect.advisor.domain.model.AdvisorSector;
import com.advisorconnect.advisor.domain.port.out.AdvisorApplicationRepository;
import com.advisorconnect.advisor.domain.port.out.UserEmailCacheRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end coverage for the two defects Phase 5 exists to fix, exercised against a real
 * Postgres and a real Kafka broker rather than mocks of them:
 *
 * <ol>
 *   <li><strong>PII was stored in clear text.</strong> The entity claimed pgcrypto encrypted the
 *       legal-identity columns; nothing did. Asserted here by reading the columns back over raw
 *       JDBC, bypassing the JPA converter entirely — the only vantage point from which the
 *       difference is visible, and the same one an attacker with a database dump would have.</li>
 *   <li><strong>Decision events carried no email.</strong> notification-service had no address to
 *       send to, so approval mail was never delivered. Asserted here by publishing a real
 *       {@code user.registered}, approving through the real HTTP endpoint, and consuming the
 *       resulting {@code advisor.approved} off the broker.</li>
 * </ol>
 *
 * <p>Run by Failsafe under {@code mvn verify}; requires Docker. {@code mvn test} skips
 * {@code *IT} classes entirely.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AdvisorServiceIT {

    private static final String TOPIC_USER_REGISTERED = "user.registered";
    private static final String TOPIC_ADVISOR_APPROVED = "advisor.approved";
    private static final String TOPIC_ADVISOR_REJECTED = "advisor.rejected";
    private static final String TOPIC_BOOKING_COMPLETED = "booking.completed";

    private static final String LEGAL_FIRST_NAME = "Wilhelmina";
    private static final String LEGAL_LAST_NAME = "Ndlovu";
    private static final String DATE_OF_BIRTH = "1985-03-14";
    private static final String ADDRESS = "42 Privet Drive, Little Whinging";
    private static final String COUNTRY = "GB";

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
        // Events are produced as JSON strings; the consumer ignores type headers and materialises
        // a HashMap, which is exactly what makes the contract survive across services.
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private AdvisorApplicationRepository applicationRepository;
    @Autowired private UserEmailCacheRepository userEmailCacheRepository;
    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Consumer<String, String>> openConsumers = new ArrayList<>();

    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        adminId = UUID.randomUUID();
    }

    @AfterEach
    void closeConsumers() {
        openConsumers.forEach(Consumer::close);
        openConsumers.clear();
    }

    // ═══════════════════════════════════════════ the email regression, end to end

    @Test
    @DisplayName("advisor.approved carries the email learned from user.registered")
    void approvalEventCarriesEmailFromRegistrationStream() {
        String email = "ada-" + userId + "@example.com";
        Consumer<String, String> approvals = subscribe(TOPIC_ADVISOR_APPROVED);

        publishUserRegistered(userId, email);
        awaitCachedEmail(email);

        UUID applicationId = submitApplication();
        approve(applicationId);

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(approvals, TOPIC_ADVISOR_APPROVED, Duration.ofSeconds(30));

        assertThat(record.key()).isEqualTo(userId.toString());
        assertThat(record.value())
                .contains("\"eventType\":\"ADVISOR_APPROVED\"")
                .contains("\"advisorId\":\"" + userId + "\"")
                .contains("\"reviewedBy\":\"" + adminId + "\"")
                .contains("\"email\":\"" + email + "\"");
    }

    @Test
    @DisplayName("advisor.rejected likewise carries the cached email")
    void rejectionEventCarriesEmail() {
        String email = "grace-" + userId + "@example.com";
        Consumer<String, String> rejections = subscribe(TOPIC_ADVISOR_REJECTED);

        publishUserRegistered(userId, email);
        awaitCachedEmail(email);

        UUID applicationId = submitApplication();
        reject(applicationId, "insufficient evidence");

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(rejections, TOPIC_ADVISOR_REJECTED, Duration.ofSeconds(30));

        assertThat(record.value())
                .contains("\"eventType\":\"ADVISOR_REJECTED\"")
                .contains("\"reason\":\"insufficient evidence\"")
                .contains("\"email\":\"" + email + "\"");
    }

    /**
     * The cache is fed by another service's topic and can legitimately lag. An admin's decision
     * must not be blocked by that, so the event ships with an empty email and the notification is
     * what degrades — not the approval.
     */
    @Test
    @DisplayName("an approval with no cached email still succeeds, carrying an empty one")
    void approvalWithoutCachedEmailStillSucceeds() {
        Consumer<String, String> approvals = subscribe(TOPIC_ADVISOR_APPROVED);

        UUID applicationId = submitApplication();
        approve(applicationId);

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(approvals, TOPIC_ADVISOR_APPROVED, Duration.ofSeconds(30));
        assertThat(record.value()).contains("\"email\":\"\"");
    }

    @Test
    @DisplayName("redelivered user.registered events upsert rather than collide on the primary key")
    void registrationRedeliveryIsIdempotent() {
        String email = "linus-" + userId + "@example.com";

        publishUserRegistered(userId, email);
        publishUserRegistered(userId, email);
        publishUserRegistered(userId, email);
        awaitCachedEmail(email);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_email_cache WHERE id = ?", Integer.class, userId);
        assertThat(rows).isEqualTo(1);
    }

    // ═════════════════════════════════════ the encryption regression, at the disk

    /**
     * Read over raw JDBC on purpose. Going through the repository would hand back plaintext
     * whether or not encryption is wired up, because the converter runs on the way out — the
     * assertion would pass against the very bug it is meant to catch.
     */
    @Test
    @DisplayName("PII columns hold ciphertext on disk, not the values that were submitted")
    void piiColumnsAreCiphertextAtRest() {
        UUID applicationId = submitApplication();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT legal_first_name_enc, legal_last_name_enc, date_of_birth_enc, "
                        + "address_enc, country_enc FROM advisor_applications WHERE id = ?",
                applicationId);

        assertThat((String) row.get("legal_first_name_enc"))
                .isNotNull().isNotEqualTo(LEGAL_FIRST_NAME).doesNotContain(LEGAL_FIRST_NAME);
        assertThat((String) row.get("legal_last_name_enc"))
                .isNotNull().isNotEqualTo(LEGAL_LAST_NAME).doesNotContain(LEGAL_LAST_NAME);
        assertThat((String) row.get("date_of_birth_enc"))
                .isNotNull().isNotEqualTo(DATE_OF_BIRTH).doesNotContain(DATE_OF_BIRTH);
        assertThat((String) row.get("address_enc"))
                .isNotNull().isNotEqualTo(ADDRESS).doesNotContain("Privet Drive");
        assertThat((String) row.get("country_enc"))
                .isNotNull().isNotEqualTo(COUNTRY);
    }

    @Test
    @DisplayName("the encrypted values decrypt back to the originals when read through JPA")
    void piiRoundTripsThroughTheRepository() {
        UUID applicationId = submitApplication();

        AdvisorApplication app = applicationRepository.findById(applicationId).orElseThrow();

        assertThat(app.getLegalFirstName()).isEqualTo(LEGAL_FIRST_NAME);
        assertThat(app.getLegalLastName()).isEqualTo(LEGAL_LAST_NAME);
        assertThat(app.getDateOfBirth()).isEqualTo(DATE_OF_BIRTH);
        assertThat(app.getAddressFull()).isEqualTo(ADDRESS);
        assertThat(app.getCountry()).isEqualTo(COUNTRY);
    }

    /**
     * A fresh IV per encryption is the property that keeps GCM safe under a fixed key. Two
     * applicants who happen to live in the same country must not produce identical columns —
     * otherwise the column leaks equality across every row in the table.
     */
    @Test
    @DisplayName("identical plaintext in two rows produces different ciphertext")
    void identicalPlaintextDoesNotProduceIdenticalColumns() {
        UUID first = submitApplication();
        userId = UUID.randomUUID();
        UUID second = submitApplication();

        String firstCountry = jdbcTemplate.queryForObject(
                "SELECT country_enc FROM advisor_applications WHERE id = ?", String.class, first);
        String secondCountry = jdbcTemplate.queryForObject(
                "SELECT country_enc FROM advisor_applications WHERE id = ?", String.class, second);

        assertThat(firstCountry).isNotEqualTo(secondCountry);
    }

    /**
     * Encrypting these columns makes them unsearchable, which is the intended trade. Pinning it
     * here so that a future "just add a WHERE clause on the date of birth" is caught as the
     * design change it is, not discovered as a silently empty result set.
     */
    @Test
    @DisplayName("an encrypted column cannot be matched by its plaintext in SQL")
    void encryptedColumnsAreNotSearchable() {
        submitApplication();

        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM advisor_applications WHERE date_of_birth_enc = ?",
                Integer.class, DATE_OF_BIRTH);

        assertThat(matches).isZero();
    }

    // ══════════════════════════════════════════════ admin list stays PII-free

    @Test
    @DisplayName("GET /advisors/applications returns summaries with no PII and a document count")
    void adminListIsPiiFree() {
        submitApplication();

        ResponseEntity<String> response = restTemplate.exchange(
                "/advisors/applications?status=PENDING", HttpMethod.GET,
                new HttpEntity<>(adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"username\":\"advisor-")
                .contains("\"documentCount\":2")
                .doesNotContain(LEGAL_FIRST_NAME)
                .doesNotContain(LEGAL_LAST_NAME)
                .doesNotContain(DATE_OF_BIRTH)
                .doesNotContain("Privet Drive")
                .doesNotContain("s3/passport.pdf");
    }

    @Test
    @DisplayName("GET /advisors/applications is refused outright without an ADMIN role")
    void adminListRejectsNonAdmins() {
        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.set("X-User-Id", userId.toString());
        userHeaders.set("X-User-Role", "USER");

        ResponseEntity<String> response = restTemplate.exchange(
                "/advisors/applications", HttpMethod.GET,
                new HttpEntity<>(userHeaders), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ═════════════════════════════════════════════════════ reviews, end to end

    /**
     * The whole Phase 9 loop against real infrastructure: booking-service's
     * {@code booking.completed} lands on the broker, advisor-service turns it into local review
     * eligibility, the client posts a review over real HTTP, and the advisor's denormalised rating
     * moves in the same transaction that stored it.
     *
     * <p>The rating is asserted through the public profile endpoint rather than the repository,
     * because the number a prospective client actually sees is the thing under test. The advisor
     * starts at {@code 0.00} over zero reviews — the profile promoted at approval time — so a
     * single 5 must land as exactly {@code 5.00} over one.
     */
    @Test
    @DisplayName("a completed booking earns a review, which moves the advisor's rating")
    void completedBookingEarnsAReviewThatUpdatesTheRating() {
        String username = approvedAdvisor();
        UUID reviewerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        // Before: promoted from an application, never rated.
        AdvisorPublicDto before = fetchProfile(username);
        assertThat(before.getReviewCount()).isZero();

        publishBookingCompleted(bookingId, reviewerId, userId);
        awaitReviewEligibility(bookingId);

        ResponseEntity<String> submitted = submitReview(userId, reviewerId, 5, "clear and decisive");
        assertThat(submitted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(submitted.getBody())
                .contains("\"rating\":5")
                .contains("\"comment\":\"clear and decisive\"")
                .contains("\"userId\":\"" + reviewerId + "\"")
                // An internal eligibility token, never published to a reader of the profile.
                .doesNotContain("bookingId");

        AdvisorPublicDto after = fetchProfile(username);
        assertThat(after.getAverageRating()).isEqualByComparingTo("5.00");
        assertThat(after.getReviewCount()).isEqualTo(1);

        // The review is readable on the public listing without any identity headers at all.
        ResponseEntity<String> listing = restTemplate.getForEntity(
                "/advisors/" + userId + "/reviews", String.class);
        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody()).contains("\"comment\":\"clear and decisive\"");
    }

    /**
     * The second review is the defence that matters: accepting one spends the booking, and a spent
     * booking is no longer an unreviewed one, so the client has nothing left to review with. It
     * must answer 409 rather than a constraint-violation 500 — and, critically, must not have
     * moved the rating on its way to failing.
     */
    @Test
    @DisplayName("a second review against the same completed booking is refused with 409")
    void secondReviewIsRefused() {
        String username = approvedAdvisor();
        UUID reviewerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        publishBookingCompleted(bookingId, reviewerId, userId);
        awaitReviewEligibility(bookingId);

        assertThat(submitReview(userId, reviewerId, 5, "first").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> second = submitReview(userId, reviewerId, 1, "changed my mind");
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // The refused attempt left neither a second review row nor a dent in the average.
        AdvisorPublicDto profile = fetchProfile(username);
        assertThat(profile.getAverageRating()).isEqualByComparingTo("5.00");
        assertThat(profile.getReviewCount()).isEqualTo(1);

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reviews WHERE advisor_id = ?", Integer.class, userId);
        assertThat(rows).isEqualTo(1);
    }

    /**
     * The eligibility gate seen from the other side: a client who never booked this advisor has no
     * completion record, so the same 409 applies. Without this the rating would be open to anyone
     * with an account.
     */
    @Test
    @DisplayName("a client with no completed session cannot review at all")
    void reviewWithoutACompletedSessionIsRefused() {
        String username = approvedAdvisor();

        ResponseEntity<String> response =
                submitReview(userId, UUID.randomUUID(), 1, "never met them");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(fetchProfile(username).getReviewCount()).isZero();
    }

    /**
     * Replaying the topic must not hand back review slots. The consumer skips an existing row
     * rather than upserting it, precisely because {@code reviewed} is owned locally and the event
     * knows nothing about it.
     */
    @Test
    @DisplayName("redelivered booking.completed events do not re-open a spent review slot")
    void bookingCompletedRedeliveryDoesNotReopenTheReviewSlot() {
        approvedAdvisor();
        UUID reviewerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        publishBookingCompleted(bookingId, reviewerId, userId);
        awaitReviewEligibility(bookingId);
        assertThat(submitReview(userId, reviewerId, 4, "solid").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Replay the same event twice more, as a consumer-group offset reset would.
        publishBookingCompleted(bookingId, reviewerId, userId);
        publishBookingCompleted(bookingId, reviewerId, userId);
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM booking_completion_records WHERE booking_id = ?",
                        Integer.class, bookingId)).isEqualTo(1));

        // The flag survived the replay, so the slot is still spent.
        Boolean reviewed = jdbcTemplate.queryForObject(
                "SELECT reviewed FROM booking_completion_records WHERE booking_id = ?",
                Boolean.class, bookingId);
        assertThat(reviewed).isTrue();
        assertThat(submitReview(userId, reviewerId, 1, "again").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    /** Posting a review is not a public act, even though reading the listing is. */
    @Test
    @DisplayName("POST /advisors/{id}/reviews without identity headers is refused")
    void reviewSubmissionRequiresAuthentication() {
        approvedAdvisor();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/advisors/" + userId + "/reviews", HttpMethod.POST,
                new HttpEntity<>("{\"rating\":5}", headers), String.class);

        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    // ══════════════════════════════════════════════════════════════════ helpers

    /** Runs an application through to approval and returns the resulting profile's username. */
    private String approvedAdvisor() {
        UUID applicationId = submitApplication();
        approve(applicationId);
        return "advisor-" + userId.toString().substring(0, 8);
    }

    private AdvisorPublicDto fetchProfile(String username) {
        ResponseEntity<AdvisorPublicDto> response =
                restTemplate.getForEntity("/advisors/" + username, AdvisorPublicDto.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<String> submitReview(
            UUID advisorId, UUID reviewerId, int rating, String comment) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", reviewerId.toString());
        headers.set("X-User-Role", "USER");

        String body = "{\"rating\":%d,\"comment\":\"%s\"}".formatted(rating, comment);
        return restTemplate.exchange("/advisors/" + advisorId + "/reviews", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private void publishBookingCompleted(UUID bookingId, UUID reviewerId, UUID advisorId) {
        kafkaTemplate.send(TOPIC_BOOKING_COMPLETED, bookingId.toString(),
                "{\"eventType\":\"BOOKING_COMPLETED\",\"bookingId\":\"" + bookingId
                        + "\",\"userId\":\"" + reviewerId
                        + "\",\"advisorId\":\"" + advisorId
                        + "\",\"userEmail\":\"client@example.com\"}");
    }

    /** The consumer is asynchronous; the review cannot be posted until its row exists. */
    private void awaitReviewEligibility(UUID bookingId) {
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM booking_completion_records WHERE booking_id = ?",
                        Integer.class, bookingId)).isEqualTo(1));
    }

    private UUID submitApplication() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Role", "USER");

        String body = """
                {
                  "username": "advisor-%s",
                  "professionalTitle": "Chartered Accountant",
                  "bio": "A bio comfortably longer than the fifty character minimum this endpoint enforces.",
                  "sectors": ["%s"],
                  "qualification": "ACA",
                  "fieldOfStudy": "Accounting",
                  "experienceYears": "10+",
                  "previousWork": "Audit",
                  "legalFirstName": "%s",
                  "legalLastName": "%s",
                  "dateOfBirth": "%s",
                  "addressFull": "%s",
                  "country": "%s",
                  "documents": [
                    {"s3Key": "s3/passport.pdf", "fileName": "passport.pdf",
                     "sizeBytes": 204800, "mimeType": "application/pdf"},
                    {"s3Key": "s3/degree.png", "fileName": "degree.png",
                     "sizeBytes": 51200, "mimeType": "image/png"}
                  ]
                }
                """.formatted(
                userId.toString().substring(0, 8), AdvisorSector.values()[0].name(),
                LEGAL_FIRST_NAME, LEGAL_LAST_NAME, DATE_OF_BIRTH, ADDRESS, COUNTRY);

        ResponseEntity<UUID> response = restTemplate.exchange(
                "/advisors/apply", HttpMethod.POST, new HttpEntity<>(body, headers), UUID.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void approve(UUID applicationId) {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/advisors/applications/" + applicationId + "/approve", HttpMethod.PUT,
                new HttpEntity<>("{\"notes\":\"credentials verified\"}", adminHeaders()),
                Void.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private void reject(UUID applicationId, String reason) {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/advisors/applications/" + applicationId + "/reject", HttpMethod.PUT,
                new HttpEntity<>("{\"notes\":\"" + reason + "\"}", adminHeaders()), Void.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", adminId.toString());
        headers.set("X-User-Role", "ADMIN");
        return headers;
    }

    private void publishUserRegistered(UUID id, String email) {
        kafkaTemplate.send(TOPIC_USER_REGISTERED, id.toString(),
                "{\"eventType\":\"USER_REGISTERED\",\"userId\":\"" + id
                        + "\",\"email\":\"" + email + "\"}");
    }

    private void awaitCachedEmail(String email) {
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(userEmailCacheRepository.findById(userId))
                        .get()
                        .extracting(c -> c.getEmail())
                        .isEqualTo(email));
    }

    /** Subscribed before the action under test so the record cannot be produced and missed. */
    private Consumer<String, String> subscribe(String topic) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "advisor-it-" + UUID.randomUUID(), "true", KAFKA.getBootstrapServers());
        Consumer<String, String> consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
                props, new StringDeserializer(), new StringDeserializer());
        consumer.subscribe(List.of(topic));
        consumer.poll(Duration.ofSeconds(5));
        openConsumers.add(consumer);
        return consumer;
    }
}
