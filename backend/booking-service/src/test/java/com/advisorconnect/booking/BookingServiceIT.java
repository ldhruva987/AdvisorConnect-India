package com.advisorconnect.booking;

import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.domain.model.PaymentIntentResult;
import com.advisorconnect.booking.domain.port.out.BookingRepository;
import com.advisorconnect.booking.domain.port.out.PaymentGateway;
import com.advisorconnect.booking.testsupport.StripeSignatures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end booking behaviour against a real Postgres and a real Kafka broker.
 *
 * <p>The headline case is the double-booking regression: availability used to be computed by
 * comparing a candidate slot's start against booked *start* times, so a 60-minute session
 * blocked only its first half and the advisor's 10:30 stayed on sale while they were still
 * in the 10:00 session. This test books through the HTTP API and asserts the second half is
 * gone from the calendar.
 *
 * <p>Run by Failsafe under {@code mvn verify}; requires a working Docker daemon.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BookingServiceIT {

    private static final String DATE = "2026-08-01";

    /** Signing secret for the webhook leg; injected below so it matches what the app reads. */
    private static final String WEBHOOK_SECRET = "whsec_integration_test_secret";

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
        registry.add("stripe.webhook-secret", () -> WEBHOOK_SECRET);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The one collaborator that is stubbed. Everything else here is real — real Postgres, real
     * Kafka, real Spring Security, real Stripe signature verification on the webhook leg — but
     * creating a PaymentIntent would mean an outbound call to Stripe's live API with a real
     * secret key, which no test may do. The stub hands back deterministic, unique ids so the
     * webhook test can reference the exact intent a booking was created against.
     */
    @MockBean
    private PaymentGateway paymentGateway;

    private final AtomicInteger intentCounter = new AtomicInteger();

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.createPaymentIntent(any(), any(), any()))
                .willAnswer(invocation -> {
                    String id = "pi_it_" + intentCounter.incrementAndGet();
                    return new PaymentIntentResult(id, id + "_secret_test");
                });
    }

    // ---------------------------------------------------------- the double-booking regression

    @Test
    @DisplayName("a 60-minute booking removes both of its 30-minute slots from availability")
    void sixtyMinuteBookingBlocksItsSecondHalf() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        assertThat(availableSlots(advisorId)).contains(iso(10, 0), iso(10, 30));

        createBooking(clientId, advisorId, iso(10, 0), 60).andExpect(status().isCreated());

        List<String> slots = availableSlots(advisorId);
        assertThat(slots)
                .as("the 10:00 session runs until 11:00, so neither half may still be on sale")
                .doesNotContain(iso(10, 0), iso(10, 30));
        assertThat(slots).contains(iso(9, 30), iso(11, 0));
    }

    @Test
    @DisplayName("a second client cannot be sold a slot overlapping an existing session")
    void overlappingSlotIsNotOfferedToASecondClient() throws Exception {
        UUID advisorId = UUID.randomUUID();

        createBooking(UUID.randomUUID(), advisorId, iso(13, 0), 60).andExpect(status().isCreated());
        // A 30-minute booking butting up against the end of the first one is legitimate.
        createBooking(UUID.randomUUID(), advisorId, iso(14, 0), 30).andExpect(status().isCreated());

        assertThat(availableSlots(advisorId))
                .doesNotContain(iso(13, 0), iso(13, 30), iso(14, 0))
                .contains(iso(12, 30), iso(14, 30));
    }

    @Test
    @DisplayName("cancelling a session puts its slots back on the calendar")
    void cancellingRestoresAvailability() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        UUID bookingId = bookingIdOf(createBooking(clientId, advisorId, iso(15, 0), 60));

        assertThat(availableSlots(advisorId)).doesNotContain(iso(15, 0), iso(15, 30));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", clientId.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        assertThat(availableSlots(advisorId)).contains(iso(15, 0), iso(15, 30));
    }

    @Test
    @DisplayName("the persisted end time is derived from the duration")
    void endTimeIsPersisted() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        UUID bookingId = bookingIdOf(createBooking(clientId, advisorId, iso(9, 0), 60));

        Booking persisted = bookingRepository.findById(bookingId).orElseThrow();
        assertThat(persisted.getSessionEndDateTime())
                .isEqualTo(persisted.getSessionDateTime().plus(60, ChronoUnit.MINUTES));
        assertThat(persisted.getAmountCharged()).isEqualByComparingTo(new BigDecimal("90.00"));
        // A new booking is unpaid. It used to be written straight to CONFIRMED, which meant every
        // booking looked paid whether or not a card was ever charged.
        assertThat(persisted.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(persisted.getStripePaymentIntentId()).doesNotContain("placeholder");
    }

    // ------------------------------------------------------------------------ GET /bookings/me

    @Test
    @DisplayName("GET /bookings/me returns the caller's bookings and nobody else's")
    void myBookingsIsScopedToTheCaller() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID me = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();

        createBooking(me, advisorId, iso(9, 30), 30).andExpect(status().isCreated());
        createBooking(me, advisorId, iso(11, 0), 60).andExpect(status().isCreated());
        createBooking(someoneElse, advisorId, iso(16, 0), 30).andExpect(status().isCreated());

        mockMvc.perform(get("/bookings/me")
                        .header("X-User-Id", me.toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userId").value(me.toString()))
                .andExpect(jsonPath("$[1].userId").value(me.toString()));
    }

    @Test
    @DisplayName("GET /bookings/me is empty for a caller who has never booked")
    void myBookingsIsEmptyForANewUser() throws Exception {
        mockMvc.perform(get("/bookings/me")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------------------------------------------------------------------- ownership on read

    @Test
    @DisplayName("the advisor on a session can read it, an unrelated user cannot")
    void ownershipIsEnforcedOnRead() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        UUID bookingId = bookingIdOf(createBooking(clientId, advisorId, iso(12, 0), 30));

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .header("X-User-Id", advisorId.toString())
                        .header("X-User-Role", "ADVISOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));

        // An unrelated caller must not get the booking back. The failure surfaces as a
        // SecurityException, which booking-service has no @ControllerAdvice for, so it
        // becomes a 500 — the property asserted here is that the payload is not disclosed.
        assertThat(readStatusFor(bookingId, UUID.randomUUID(), "USER"))
                .as("an unrelated user must not receive the booking")
                .isNotEqualTo(200);
    }

    // -------------------------------------------------------------------- request validation

    @Test
    @DisplayName("a 45-minute booking is refused at the edge and never persisted")
    void unsupportedDurationIsRejected() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        createBooking(clientId, advisorId, iso(10, 0), 45)
                .andExpect(status().isBadRequest());

        assertThat(bookingRepository.findByUserId(clientId)).isEmpty();
    }

    // ------------------------------------------------------ payment lifecycle, end to end

    @Test
    @DisplayName("a genuinely signed payment_intent.succeeded moves the booking PENDING → CONFIRMED")
    void stripeWebhookConfirmsTheBooking() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        var created = createBooking(clientId, advisorId, iso(10, 0), 30)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.status").value("PENDING"))
                .andExpect(jsonPath("$.clientSecret").isNotEmpty());

        UUID bookingId = bookingIdOf(created);
        String paymentIntentId = paymentIntentIdOf(created);

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);

        postSignedWebhook(stripeEvent("payment_intent.succeeded", paymentIntentId))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .as("Stripe's webhook is the only thing that may confirm a booking")
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a genuinely signed payment_intent.payment_failed moves the booking to FAILED "
            + "and frees the slot")
    void stripeWebhookFailsTheBooking() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        var created = createBooking(clientId, advisorId, iso(11, 0), 60)
                .andExpect(status().isCreated());
        UUID bookingId = bookingIdOf(created);

        assertThat(availableSlots(advisorId))
                .as("an unpaid booking still holds its slot while checkout is in flight")
                .doesNotContain(iso(11, 0), iso(11, 30));

        postSignedWebhook(stripeEvent("payment_intent.payment_failed", paymentIntentIdOf(created)))
                .andExpect(status().isOk());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.FAILED);
        assertThat(availableSlots(advisorId))
                .as("a declined card must hand the time back")
                .contains(iso(11, 0), iso(11, 30));
    }

    @Test
    @DisplayName("a forged webhook is rejected end to end and cannot confirm anybody's booking")
    void forgedWebhookCannotConfirmABooking() throws Exception {
        UUID advisorId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        var created = createBooking(clientId, advisorId, iso(14, 0), 30)
                .andExpect(status().isCreated());
        UUID bookingId = bookingIdOf(created);

        String payload = stripeEvent("payment_intent.succeeded", paymentIntentIdOf(created));
        mockMvc.perform(post("/bookings/webhooks/stripe")
                        .header("Stripe-Signature",
                                StripeSignatures.sign(payload, "whsec_an_attackers_guess"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(bookingRepository.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    // ------------------------------------------------------------------------------- helpers

    private org.springframework.test.web.servlet.ResultActions postSignedWebhook(String payload)
            throws Exception {
        return mockMvc.perform(post("/bookings/webhooks/stripe")
                // No X-User-* headers: Stripe never sends them. That this reaches the controller
                // at all is the permitAll() rule in SecurityConfig doing its job.
                .header("Stripe-Signature", StripeSignatures.sign(payload, WEBHOOK_SECRET))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private static String stripeEvent(String type, String paymentIntentId) {
        return """
                {
                  "id": "evt_it_%s",
                  "object": "event",
                  "api_version": "2023-10-16",
                  "created": 1700000000,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 5000,
                      "currency": "usd"
                    }
                  }
                }
                """.formatted(paymentIntentId, type, paymentIntentId);
    }

    private UUID bookingIdOf(org.springframework.test.web.servlet.ResultActions created)
            throws Exception {
        return UUID.fromString(createResponse(created).get("booking").get("id").asText());
    }

    private String paymentIntentIdOf(org.springframework.test.web.servlet.ResultActions created)
            throws Exception {
        return createResponse(created).get("booking").get("stripePaymentIntentId").asText();
    }

    /**
     * POST /bookings returns a {@code BookingResponse}, so the booking sits under
     * {@code "booking"} rather than at the top level as it did before the client secret had to
     * be returned alongside it.
     */
    private com.fasterxml.jackson.databind.JsonNode createResponse(
            org.springframework.test.web.servlet.ResultActions created) throws Exception {
        return objectMapper.readTree(created.andReturn().getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions createBooking(
            UUID userId, UUID advisorId, String sessionDateTime, int durationMinutes) throws Exception {
        String body = """
                {
                  "advisorId": "%s",
                  "sessionDateTime": "%s",
                  "durationMinutes": %d,
                  "stripePaymentMethodId": "pm_test_123"
                }
                """.formatted(advisorId, sessionDateTime, durationMinutes);

        return mockMvc.perform(post("/bookings")
                .header("X-User-Id", userId.toString())
                .header("X-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private List<String> availableSlots(UUID advisorId) throws Exception {
        String json = mockMvc.perform(get("/bookings/availability/{advisorId}", advisorId)
                        .param("date", DATE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readerForListOf(String.class).readValue(json);
    }

    /** Performs the read and reports the status, tolerating a handler exception. */
    private int readStatusFor(UUID bookingId, UUID callerId, String role) throws Exception {
        try {
            return mockMvc.perform(get("/bookings/{id}", bookingId)
                            .header("X-User-Id", callerId.toString())
                            .header("X-User-Role", role))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            // MockMvc rethrows an unhandled handler exception rather than rendering a status.
            assertThat(rootCause(e)).isInstanceOf(SecurityException.class);
            return 500;
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String iso(int hour, int minute) {
        return Instant.parse(DATE + "T00:00:00Z")
                .plus(hour * 60L + minute, ChronoUnit.MINUTES)
                .toString();
    }
}
