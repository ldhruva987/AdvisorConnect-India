package com.advisorconnect.notification.application;

import com.advisorconnect.notification.domain.model.Notification;
import com.advisorconnect.notification.domain.port.out.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService service;

    // ------------------------------------------------------------------------------- create

    @Test
    @DisplayName("a new notification is unread, timestamped and owned by the recipient")
    void createPersistsAnUnreadNotification() {
        givenSaveEchoes();
        Instant before = Instant.now();

        Notification created = service.create(
                owner, "BOOKING_CONFIRMED", "Booking confirmed", "Your session is booked.");

        Notification saved = captureSaved();
        assertThat(saved.getUserId()).isEqualTo(owner);
        assertThat(saved.getType()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(saved.getTitle()).isEqualTo("Booking confirmed");
        assertThat(saved.getBody()).isEqualTo("Your session is booked.");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(created).isSameAs(saved);
    }

    // --------------------------------------------------------------------------------- list

    @Test
    @DisplayName("listing is scoped to the caller and passes the page request through")
    void listForUserIsScoped() {
        Pageable pageable = PageRequest.of(2, 5);
        Page<Notification> page = new PageImpl<>(List.of(notification(owner, false)), pageable, 11);
        given(notificationRepository.findByUserId(owner, pageable)).willReturn(page);

        Page<Notification> result = service.listForUser(owner, pageable);

        assertThat(result.getTotalElements()).isEqualTo(11);
        assertThat(result.getContent()).singleElement()
                .satisfies(n -> assertThat(n.getUserId()).isEqualTo(owner));
        verify(notificationRepository).findByUserId(owner, pageable);
    }

    @Test
    @DisplayName("an empty inbox is an empty page, not an error")
    void emptyInbox() {
        Pageable pageable = PageRequest.of(0, 20);
        given(notificationRepository.findByUserId(owner, pageable)).willReturn(Page.empty(pageable));

        assertThat(service.listForUser(owner, pageable)).isEmpty();
    }

    // ----------------------------------------------------------------------------- markRead

    @Test
    @DisplayName("the owner can mark their own notification read")
    void ownerCanMarkRead() {
        Notification mine = notification(owner, false);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(mine));
        givenSaveEchoes();

        Notification updated = service.markRead(notificationId, owner);

        assertThat(updated.isRead()).isTrue();
        verify(notificationRepository).save(mine);
    }

    @Test
    @DisplayName("marking an already-read notification read again is a no-op, not an error")
    void markReadIsIdempotent() {
        Notification alreadyRead = notification(owner, true);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(alreadyRead));

        Notification result = service.markRead(notificationId, owner);

        assertThat(result.isRead()).isTrue();
        // No second write: a client retrying must not churn the row.
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("marking somebody else's notification read is refused and writes nothing")
    void strangerCannotMarkRead() {
        Notification someoneElses = notification(owner, false);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.markRead(notificationId, stranger))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Not authorized");

        assertThat(someoneElses.isRead())
                .as("the refused call must not have mutated the entity either")
                .isFalse();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unknown id is a not-found, distinct from a forbidden")
    void unknownIdThrowsNotFound() {
        given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(notificationId, owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(notificationId.toString());

        verify(notificationRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------------- helpers

    private void givenSaveEchoes() {
        given(notificationRepository.save(any(Notification.class)))
                .willAnswer(inv -> inv.getArgument(0));
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private Notification notification(UUID userId, boolean read) {
        return Notification.builder()
                .id(notificationId)
                .userId(userId)
                .type("BOOKING_CONFIRMED")
                .title("Booking confirmed")
                .body("Your session is booked.")
                .read(read)
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
    }
}
