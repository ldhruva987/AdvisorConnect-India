package com.advisorconnect.chat.application;

import com.advisorconnect.chat.domain.model.Message;
import com.advisorconnect.chat.domain.port.out.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;

    public Message sendMessage(UUID userId, UUID advisorId, String text, String senderType) {
        Message message = Message.builder()
                .userId(userId)
                .advisorId(advisorId)
                .senderId("user".equals(senderType) ? userId : advisorId)
                .senderType(senderType)
                .text(text)
                .messageId(UUID.randomUUID())
                .createdAt(Instant.now())
                .read(false)
                .build();
        return messageRepository.save(message);
    }

    public List<Message> getMessages(UUID userId, UUID advisorId, int limit) {
        return messageRepository.findByConversation(userId, advisorId, limit);
    }
}
