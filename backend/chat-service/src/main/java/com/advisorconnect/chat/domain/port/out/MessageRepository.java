package com.advisorconnect.chat.domain.port.out;

import com.advisorconnect.chat.domain.model.Message;

import java.util.List;
import java.util.UUID;

public interface MessageRepository {

    Message save(Message message);

    /**
     * The most recent {@code limit} messages of a conversation, newest first.
     *
     * @param limit a hard cap on rows returned, not a page size
     */
    List<Message> findByConversation(UUID userId, UUID advisorId, int limit);

    /**
     * Flags every currently-unread message of a conversation as read.
     *
     * @return how many messages were actually flipped; {@code 0} when there was nothing unread
     */
    int markConversationRead(UUID userId, UUID advisorId);
}
