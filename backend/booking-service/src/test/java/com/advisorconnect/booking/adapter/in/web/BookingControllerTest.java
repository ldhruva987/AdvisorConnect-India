package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.adapter.in.web.dto.BookingResponse;
import com.advisorconnect.booking.application.BookingService;
import com.advisorconnect.booking.domain.model.Booking;
import com.advisorconnect.booking.domain.model.BookingStatus;
import com.advisorconnect.booking.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer behaviour of {@code BookingController}: request validation, and that the
 * identity headers are threaded into the service's authorisation decision.
 *
 * <p>Authentication enforcement itself lives in {@code BookingControllerSecurityTest}.
 */
@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
class BookingControllerTest {

    private static final String USER_ID = "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b";
    private static final String ADVISOR_ID = "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    // ----------------------------------------------------------------- duration validation

    @ParameterizedTest
    @ValueSource(ints = {30, 60})
    @DisplayName("the two real session lengths pass validation and reach the service")
    void supportedDurationsAreAccepted(int duration) throws Exception {
        given(bookingService.createBooking(any(), eq(UUID.fromString(USER_ID))))
                .willReturn(sampleResponse(duration));

        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duration)))
                .andExpect(status().isCreated())
                // The booking is now nested under "booking": the endpoint returns a
                // BookingResponse so it can also hand back the Stripe client secret.
                .andExpect(jsonPath("$.booking.durationMinutes").value(duration));
    }

    @Test
    @DisplayName("the create response carries the Stripe client secret alongside a PENDING booking")
    void createResponseCarriesTheClientSecret() throws Exception {
        given(bookingService.createBooking(any(), eq(UUID.fromString(USER_ID))))
                .willReturn(sampleResponse(30));

        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(30)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").value("pi_test_secret_abc"))
                .andExpect(jsonPath("$.booking.status").value("PENDING"))
                .andExpect(jsonPath("$.booking.stripePaymentIntentId").value("pi_test"));
    }

    @Test
    @DisplayName("durationMinutes=45 is rejected with 400 — the old @Min(30)/@Max(60) let it through")
    void fortyFiveMinutesIsRejected() throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(45)))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 29, 31, 59, 61, 120})
    @DisplayName("every other duration is rejected with 400 too")
    void otherDurationsAreRejected(int duration) throws Exception {
        mockMvc.perform(post("/bookings")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(duration)))
                .andExpect(status().isBadRequest());

        verify(bookingService, never()).createBooking(any(), any());
    }

    // ------------------------------------------------------------------- identity plumbing

    @Test
    @DisplayName("GET /bookings/{id} passes the caller's id through, with isAdmin=false for a USER")
    void getPassesRequesterIdentity() throws Exception {
        UUID bookingId = UUID.randomUUID();
        given(bookingService.getBooking(bookingId, UUID.fromString(USER_ID), false))
                .willReturn(sampleBooking(30));

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        verify(bookingService).getBooking(bookingId, UUID.fromString(USER_ID), false);
    }

    @Test
    @DisplayName("an ADMIN role is translated into isAdmin=true")
    void adminRoleIsRecognised() throws Exception {
        UUID bookingId = UUID.randomUUID();
        given(bookingService.getBooking(bookingId, UUID.fromString(USER_ID), true))
                .willReturn(sampleBooking(30));

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());

        verify(bookingService).getBooking(bookingId, UUID.fromString(USER_ID), true);
    }

    @Test
    @DisplayName("GET /bookings/me is routed to the literal path, not to GET /bookings/{id}")
    void myBookingsHasItsOwnRoute() throws Exception {
        given(bookingService.getMyBookings(UUID.fromString(USER_ID)))
                .willReturn(List.of(sampleBooking(30)));

        mockMvc.perform(get("/bookings/me")
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER_ID));

        verify(bookingService).getMyBookings(UUID.fromString(USER_ID));
    }

    // ------------------------------------------------------------------------------ helpers

    private static String createBody(int durationMinutes) {
        return """
                {
                  "advisorId": "%s",
                  "sessionDateTime": "2026-08-01T10:00:00Z",
                  "durationMinutes": %d,
                  "stripePaymentMethodId": "pm_test_123"
                }
                """.formatted(ADVISOR_ID, durationMinutes);
    }

    /** What POST /bookings now returns: a freshly created, still-unpaid booking plus its secret. */
    private static BookingResponse sampleResponse(int durationMinutes) {
        return new BookingResponse(sampleBooking(durationMinutes, BookingStatus.PENDING),
                "pi_test_secret_abc");
    }

    private static Booking sampleBooking(int durationMinutes) {
        return sampleBooking(durationMinutes, BookingStatus.CONFIRMED);
    }

    private static Booking sampleBooking(int durationMinutes, BookingStatus status) {
        Instant start = Instant.parse("2026-08-01T10:00:00Z");
        return Booking.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(USER_ID))
                .advisorId(UUID.fromString(ADVISOR_ID))
                .sessionDateTime(start)
                .sessionEndDateTime(start.plus(durationMinutes, ChronoUnit.MINUTES))
                .durationMinutes(durationMinutes)
                .amountCharged(new BigDecimal("50.00"))
                .status(status)
                .stripePaymentIntentId("pi_test")
                .build();
    }
}
