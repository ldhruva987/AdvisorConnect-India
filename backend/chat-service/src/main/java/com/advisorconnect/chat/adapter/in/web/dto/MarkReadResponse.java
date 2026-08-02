package com.advisorconnect.chat.adapter.in.web.dto;

/**
 * Result of {@code PUT /chats/{counterpartyId}/read}.
 *
 * <p>A body rather than {@code 204 No Content}: the count is what lets a client decide whether to
 * refresh anything. Zero means the conversation was already read, and the client can leave its
 * cached message list alone instead of refetching it on every window focus.
 *
 * @param messagesMarkedRead how many messages this call flipped from unread to read
 */
public record MarkReadResponse(int messagesMarkedRead) {
}
