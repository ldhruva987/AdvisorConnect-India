package com.advisorconnect.chat.adapter.in.websocket.dto;

import com.advisorconnect.chat.adapter.in.web.dto.MessageResponse;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything the server pushes down a chat socket.
 *
 * <p>The wire protocol is a <em>flat</em>, {@code type}-discriminated object: the discriminator sits
 * alongside the payload's own fields rather than wrapping them ({@code {"type":"MESSAGE","id":…}},
 * not {@code {"type":"MESSAGE","message":{…}}}). That is the shape the browser client parses — it
 * reads {@code frame.type} and then {@code frame.id}/{@code frame.text}/… off the same object — so
 * a nested envelope would deserialise into a message with every field undefined and render nothing.
 *
 * <p>Modelled as a sealed hierarchy rather than an ad-hoc {@code Map} so that the set of frames the
 * server can emit is enumerable from one file, and so
 * {@link com.advisorconnect.chat.adapter.in.websocket.ChatWebSocketHandler#broadcastToConversation}
 * can refuse anything else at compile time. Its parameter used to be {@code Object}, which is how
 * the raw persistence entity — no {@code type} field at all, and carrying the Cassandra partition
 * key — came to be broadcast in place of a frame.
 */
public sealed interface ChatSocketFrame {

    String TYPE_MESSAGE = "MESSAGE";
    String TYPE_TYPING = "TYPING";

    /** The discriminator clients switch on. Serialised as the {@code type} property. */
    String type();

    /**
     * A newly persisted chat message.
     *
     * <p>The fields mirror {@link MessageResponse}, the REST representation of the same message,
     * exactly — deliberately, so a client can feed a socket frame and a {@code GET …/messages} row
     * through one parser. {@link #of(MessageResponse)} is the only way to build one, so the two
     * representations cannot drift.
     */
    record MessageFrame(
            String type,
            UUID id,
            String conversationId,
            UUID senderId,
            String senderType,
            String text,
            Instant createdAt,
            boolean read) implements ChatSocketFrame {

        public static MessageFrame of(MessageResponse message) {
            return new MessageFrame(
                    TYPE_MESSAGE,
                    message.id(),
                    message.conversationId(),
                    message.senderId(),
                    message.senderType(),
                    message.text(),
                    message.createdAt(),
                    message.read());
        }
    }

    /**
     * A participant is composing. There is no matching {@code TYPING_STOP}: the indicator is
     * refreshed by further frames and expires on a client-side timer, so a dropped connection
     * cannot leave it stuck on.
     *
     * @param senderId who is typing — always the JWT-verified caller, never a client-supplied id
     */
    record TypingFrame(String type, UUID senderId) implements ChatSocketFrame {

        public static TypingFrame of(UUID senderId) {
            return new TypingFrame(TYPE_TYPING, senderId);
        }
    }
}
