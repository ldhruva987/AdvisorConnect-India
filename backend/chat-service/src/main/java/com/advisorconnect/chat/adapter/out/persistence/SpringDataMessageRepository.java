package com.advisorconnect.chat.adapter.out.persistence;

import com.advisorconnect.chat.domain.model.Message;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.UUID;

interface SpringDataMessageRepository extends CassandraRepository<Message, UUID> {
}
