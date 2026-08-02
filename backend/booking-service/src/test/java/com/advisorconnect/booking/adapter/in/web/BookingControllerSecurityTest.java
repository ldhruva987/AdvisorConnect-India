package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.application.BookingService;
import com.advisorconnect.booking.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that {@code BookingController}'s {@code @PreAuthorize("isAuthenticated()")}
 * annotations are actually enforced. Booking-service previously had no Spring Security
 * configuration at all, so those annotations enforced nothing.
 */
@WebMvcTest(BookingController.class)
@Import(SecurityConfig.class)
class BookingControllerSecurityTest {

    private static final String USER_ID = "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    // ------------------------------------------------------------- public surface

    @Test
    @DisplayName("GET /bookings/availability/{advisorId} is public — visitors can see open slots")
    void availabilityIsPublic() throws Exception {
        UUID advisorId = UUID.randomUUID();
        given(bookingService.getAvailableSlots(advisorId, "2026-08-01"))
                .willReturn(List.of("09:00", "10:00"));

        mockMvc.perform(get("/bookings/availability/{advisorId}", advisorId)
                        .param("date", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("09:00"));
    }

    // --------------------------------------------------------- authentication gate

    @Test
    @DisplayName("POST /bookings without identity headers is rejected")
    void createWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(post("/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("GET /bookings/{id} without identity headers is rejected")
    void getWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UUID.randomUUID()))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("PUT /bookings/{id}/cancel without identity headers is rejected")
    void cancelWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(put("/bookings/{id}/cancel", UUID.randomUUID()))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("A non-UUID X-User-Id fails closed rather than authenticating")
    void malformedUserIdIsRejected() throws Exception {
        mockMvc.perform(get("/bookings/{id}", UUID.randomUUID())
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "USER"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("An authenticated USER reaches GET /bookings/{id}")
    void getReachableWhenAuthenticated() throws Exception {
        UUID bookingId = UUID.randomUUID();
        // getBooking now also takes the caller's identity so it can enforce ownership;
        // whether *this* caller is entitled to the booking is BookingServiceTest's concern.
        given(bookingService.getBooking(bookingId, UUID.fromString(USER_ID), false))
                .willReturn(null);

        mockMvc.perform(get("/bookings/{id}", bookingId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        verify(bookingService).getBooking(bookingId, UUID.fromString(USER_ID), false);
    }

    @Test
    @DisplayName("GET /bookings/me without identity headers is rejected")
    void myBookingsWithoutHeadersIsRejected() throws Exception {
        mockMvc.perform(get("/bookings/me"))
                .andExpect(status().is(anyOf401Or403()));

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("An authenticated USER reaches PUT /bookings/{id}/cancel")
    void cancelReachableWhenAuthenticated() throws Exception {
        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(put("/bookings/{id}/cancel", bookingId)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk());

        verify(bookingService).cancelBooking(bookingId, UUID.fromString(USER_ID));
    }

    /**
     * Which of the two an unauthenticated request yields is a Spring Security entry-point detail;
     * the property under test is that the request is refused.
     */
    private static org.hamcrest.Matcher<Integer> anyOf401Or403() {
        return org.hamcrest.Matchers.isOneOf(401, 403);
    }
}
