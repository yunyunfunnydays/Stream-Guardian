package com.streamguardian.messaging.kafka;

import com.streamguardian.ingest.dto.ChatMessage;
import com.streamguardian.messaging.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Kafka implementation of MessageProducer.
 * Sends chat messages to Kafka topic for Python service consumption.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "messaging.type", havingValue = "kafka", matchIfMissing = true)
public class KafkaMessageProducer implements MessageProducer {

    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    @Value("${kafka.topics.inbound}")
    private String inboundTopic;

    @Override
    public void send(ChatMessage message) {
        // Generate message ID if not present: timestamp + UUID suffix
        if (message.getMessageId() == null) {
            String uuid = UUID.randomUUID().toString().substring(0, 8);
            message.setMessageId("msg_" + message.getTimestamp() + "_" + uuid);
        }

        // Partition key: tenant_id:user_id for ordered processing
        String partitionKey = message.getTenantId() + ":" + message.getUserId();

        kafkaTemplate.send(inboundTopic, partitionKey, message)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send message to Kafka: messageId={}, error={}",
                        message.getMessageId(), ex.getMessage());
                } else {
                    log.debug("Message sent to Kafka: topic={}, partition={}, offset={}, messageId={}",
                        inboundTopic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        message.getMessageId());
                }
            });
    }
}
