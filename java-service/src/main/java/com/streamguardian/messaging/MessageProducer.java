package com.streamguardian.messaging;

import com.streamguardian.ingest.dto.ChatMessage;

/**
 * Interface for sending chat messages to the analysis service.
 * Implementations can use Kafka, RabbitMQ, Redis, etc.
 */
public interface MessageProducer {

    /**
     * Send a chat message for analysis
     *
     * @param message the chat message to send
     */
    void send(ChatMessage message);
}
