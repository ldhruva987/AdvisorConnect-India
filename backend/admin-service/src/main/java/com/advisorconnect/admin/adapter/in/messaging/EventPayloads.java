package com.advisorconnect.admin.adapter.in.messaging;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reading helpers for the flat {@code Map} event payloads the other services publish.
 *
 * <p>Every accessor tolerates a missing or malformed field. Admin-service is a downstream
 * observer: an event it cannot fully parse must not stall its consumer group, because the
 * consequence would be that the whole dashboard stops updating over one bad message.
 */
final class EventPayloads {

    private EventPayloads() {
    }

    /** Trimmed value, or {@code null} when absent or blank. Producers send "" for absent ids. */
    static String string(Map<String, Object> event, String key) {
        Object value = event == null ? null : event.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    static String stringOr(Map<String, Object> event, String key, String fallback) {
        String value = string(event, key);
        return value != null ? value : fallback;
    }

    static Optional<UUID> uuid(Map<String, Object> event, String key) {
        String value = string(event, key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }
}
