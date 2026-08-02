package com.advisorconnect.chat.adapter.in.web.dto;

import com.advisorconnect.chat.domain.model.ConversationParticipants;
import com.advisorconnect.chat.domain.model.Message;

import java.time.Instant;
import java.util.UUID;

/**
 * One chat message as the REST API exposes it.
 *
 * <p>Deliberately not the {@link Message} entity. The entity's shape is dictated by the Cassandra
 * primary key — {@code user_id}/{@code advisor_id} are partition-key components and
 * {@code message_id} is a clustering column — and leaking that outward would publish the storage
 * layout as the public contract, so any future re-partitioning would be a breaking API change.
 *
 * <p>The two ids are collapsed into a single opaque {@code conversationId} instead: clients group
 * messages by conversation, they have no use for the partition key's internals, and a message
 * already only ever reaches a caller who asked for that specific conversation.
 *
 * @param id             the message's own id ({@code message_id} on the row)
 * @param conversationId opaque handle, matching {@link ConversationResponse#id()} for the same
 *                       conversation
 * @param read           whether the conversation has been marked read past this message; the
 *                       schema carries one shared flag rather than one per participant
 */
public record MessageResponse(
        UUID id,
        String conversationId,
        UUID senderId,
        String senderType,
        String text,
        Instant createdAt,
        boolean read) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getMessageId(),
                ConversationParticipants.conversationId(message.getUserId(), message.getAdvisorId()),
                message.getSenderId(),
                message.getSenderType(),
                message.getText(),
                message.getCreatedAt(),
                message.isRead());
    }
}
