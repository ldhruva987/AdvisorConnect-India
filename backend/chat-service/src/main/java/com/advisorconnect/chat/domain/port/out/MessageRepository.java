package com.advisorconnect.chat.domain.port.out;

import com.advisorconnect.chat.domain.model.Message;

import java.util.List;
import java.util.UUID;

public interface MessageRepository {
    Message save(Message message);
    List<Message> findByConversation(UUID userId, UUID advisorId, int limit);
}
