package com.advisorconnect.chat.adapter.in.web.dto;

import com.advisorconnect.chat.domain.model.ConversationByAdvisor;
import com.advisorconnect.chat.domain.model.ConversationParticipants;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of an advisor's inbox: {@code GET /chats/advisor-inbox}.
 *
 * <p>Intentionally <em>not</em> the same shape as {@link ConversationResponse}. The
 * {@code conversations_by_advisor} table carries no display columns at all — no counterparty
 * username, colour or presence — so the mirror-image fields simply do not exist to return.
 * Emitting them as permanent nulls to make the two endpoints look alike would advertise data this
 * service cannot supply; the narrower record says plainly what an advisor inbox actually knows.
 */
public record AdvisorConversationResponse(
        String id,
        UUID userId,
        String lastMessage,
        Instant lastMessageAt,
        int unreadCount) {

    public static AdvisorConversationResponse from(ConversationByAdvisor conversation) {
        return new AdvisorConversationResponse(
                ConversationParticipants.conversationId(
                        conversation.getUserId(), conversation.getAdvisorId()),
                conversation.getUserId(),
                conversation.getLastMessage(),
                conversation.getLastMessageAt(),
                conversation.getUnreadCount());
    }
}
