package com.advisorconnect.chat.adapter.in.web.dto;

import com.advisorconnect.chat.domain.model.ConversationByUser;
import com.advisorconnect.chat.domain.model.ConversationParticipants;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a seeker's inbox: the conversation-list preview behind {@code GET /chats}.
 *
 * <p>{@code advisorUsername}, {@code advisorColor} and {@code isAdvisorOnline} are denormalised
 * copies owned by other services, and chat-service never writes them on the message path. They are
 * therefore nullable/false for any conversation nobody has backfilled — a client must render a
 * placeholder rather than assume they are populated.
 *
 * <p>The component is named {@code isAdvisorOnline}, not {@code advisorOnline}, because that is the
 * JSON field name the web client reads. A record component serialises under its own name, so the
 * name here <em>is</em> the wire contract.
 */
public record ConversationResponse(
        String id,
        UUID advisorId,
        String advisorUsername,
        String advisorColor,
        String lastMessage,
        Instant lastMessageAt,
        int unreadCount,
        boolean isAdvisorOnline) {

    public static ConversationResponse from(ConversationByUser conversation) {
        return new ConversationResponse(
                ConversationParticipants.conversationId(
                        conversation.getUserId(), conversation.getAdvisorId()),
                conversation.getAdvisorId(),
                conversation.getAdvisorUsername(),
                conversation.getAdvisorColor(),
                conversation.getLastMessage(),
                conversation.getLastMessageAt(),
                conversation.getUnreadCount(),
                conversation.isAdvisorOnline());
    }
}
