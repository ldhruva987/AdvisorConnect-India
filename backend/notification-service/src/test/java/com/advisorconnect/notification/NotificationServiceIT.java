package com.advisorconnect.notification;

import com.advisorconnect.notification.domain.model.Notification;
import com.advisorconnect.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole notification path against a real Kafka broker and a real Postgres: an event is
 * published to {@code booking.created}, the listener consumes it, a row lands in
 * {@code notifications}, an email send is attempted, and the user can read the notification
 * back over HTTP.
 *
 * <p>This pins the regression the service was built around. notification-service used to do
 * exactly one thing with an event — attempt an email — and booking-service never put
 * {@code userEmail} on the payload, so the send was skipped and <em>nothing at all</em> was
 * recorded. An event arrived and vanished. The assertions below therefore check both halves
 * independently, and one case deliberately omits the address to prove the durable half no
 * longer depends on the deliverable one.
 *
 * <p>Run by Failsafe under {@code mvn verify}; requires a working Docker daemon.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class NotificationServiceIT {

    /**
     * Deliberately not the production default. The From assertion below would pass by
     * coincidence if the service reverted to a hardcoded constant that happened to match the
     * configured value, so the configured value is made impossible to guess.
     */
    private static final String MAIL_FROM = "integration-sender@example.org";

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
        // The service ships ddl-auto: validate against a migrated database; the container
        // starts empty, so the schema is created from the entities for the test run.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        // Only the producer side is configured here — the consumer keeps its production
        // deserializers, since deserialization is part of what is under test. JsonSerializer
        // stamps a type header the production JsonDeserializer resolves; the payloads below
        // are plain HashMaps precisely so that header names a type it can instantiate.
        registry.add("spring.kafka.producer.value-serializer", JsonSerializer.class::getName);

        registry.add("mail.from", () -> MAIL_FROM);
    }

    /**
     * The one collaborator that is stubbed. Everything else is real, but a real
     * {@code JavaMailSender} would open an SMTP connection to a third party from a test.
     * Mocking it is also the only way to observe the send at all: {@code EmailNotificationService}
     * swallows delivery failures by design, so a broken sender is indistinguishable from a
     * skipped one at the boundary.
     */
    @MockBean
    private JavaMailSender mailSender;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private MockMvc mockMvc;

    /**
     * A consumer that has not yet been assigned its partition silently drops anything published
     * before the assignment lands, which would make every test here a flaky race.
     */
    @BeforeEach
    void awaitPartitionAssignment() {
        for (MessageListenerContainer container : listenerRegistry.getListenerContainers()) {
            ContainerTestUtils.waitForAssignment(container, 1);
        }
    }

    // -------------------------------------------------------------------- the end-to-end path

    @Test
    @DisplayName("a booking.created event persists a notification and attempts the email")
    void bookingCreatedIsRecordedAndMailed() {
        UUID userId = UUID.randomUUID();

        kafkaTemplate.send("booking.created", bookingCreated(userId, "client@example.com"));

        Notification persisted = awaitSingleNotification(userId);
        assertThat(persisted.getType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(persisted.getTitle()).isEqualTo("Booking confirmed");
        assertThat(persisted.getBody()).isNotBlank();
        assertThat(persisted.isRead()).isFalse();
        assertThat(persisted.getCreatedAt()).isNotNull();

        SimpleMailMessage sent = awaitSentMail();
        assertThat(sent.getTo()).containsExactly("client@example.com");
        assertThat(sent.getFrom())
                .as("the envelope sender must be the configured mail.from, not a hardcoded literal")
                .isEqualTo(MAIL_FROM);
        assertThat(sent.getSubject()).contains("confirmed");
    }

    @Test
    @DisplayName("the persisted notification is readable over HTTP by its owner, and nobody else")
    void thePersistedNotificationIsReadableFromTheInbox() throws Exception {
        UUID userId = UUID.randomUUID();

        kafkaTemplate.send("booking.created", bookingCreated(userId, "inbox@example.com"));
        awaitSingleNotification(userId);

        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("BOOKING_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].read").value(false));

        // A different caller's inbox is their own, and is empty.
        mockMvc.perform(get("/notifications")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ------------------------------------------------------------- the durable half stands alone

    @Test
    @DisplayName("an event with no userEmail still records the notification — the original bug")
    void anEventWithoutAnAddressIsStillRecorded() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "BOOKING_CREATED");
        event.put("bookingId", UUID.randomUUID().toString());
        event.put("userId", userId.toString());
        // No userEmail: every booking event looked like this before booking-service started
        // resolving the address, and the whole event used to disappear here.

        kafkaTemplate.send("booking.created", event);

        assertThat(awaitSingleNotification(userId).getType()).isEqualTo("BOOKING_CONFIRMED");
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("booking.cancelled produces its own notification and cancellation email")
    void bookingCancelledIsRecordedAndMailed() {
        UUID userId = UUID.randomUUID();
        Map<String, Object> event = bookingCreated(userId, "client@example.com");
        event.put("eventType", "BOOKING_CANCELLED");

        kafkaTemplate.send("booking.cancelled", event);

        assertThat(awaitSingleNotification(userId).getType()).isEqualTo("BOOKING_CANCELLED");
        assertThat(awaitSentMail().getSubject()).contains("cancelled");
    }

    // -------------------------------------------------------------------------------- helpers

    /** The payload booking-service's {@code BookingEventPublisher} sends, address included. */
    private static Map<String, Object> bookingCreated(UUID userId, String userEmail) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "BOOKING_CREATED");
        event.put("bookingId", UUID.randomUUID().toString());
        event.put("userId", userId.toString());
        event.put("advisorId", UUID.randomUUID().toString());
        event.put("userEmail", userEmail);
        return event;
    }

    /**
     * Blocks until exactly one notification exists for the user. Each test uses a fresh random
     * user id, so the rows other tests left behind in the shared container are invisible here
     * and no cleanup between tests is needed.
     */
    private Notification awaitSingleNotification(UUID userId) {
        return await().atMost(Duration.ofSeconds(30)).until(
                () -> notificationRepository
                        .findByUserId(userId, PageRequest.of(0, 10))
                        .getContent(),
                found -> found.size() == 1).get(0);
    }

    private SimpleMailMessage awaitSentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> verify(mailSender).send(any(SimpleMailMessage.class)));
        verify(mailSender).send(captor.capture());
        List<SimpleMailMessage> sent = captor.getAllValues();
        return sent.get(sent.size() - 1);
    }
}
