package com.streamguardian.messaging.kafka;

import com.streamguardian.ingest.dto.AnalysisResult;
import com.streamguardian.messaging.ResultConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka implementation of ResultConsumer.
 * Consumes analysis results from Kafka topic.
 * Note: WebSocket broadcast is handled by MCP, this consumer is for logging/monitoring.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "messaging.type", havingValue = "kafka", matchIfMissing = true)
public class KafkaResultConsumer implements ResultConsumer {

    @KafkaListener(topics = "${kafka.topics.results}", groupId = "stream-guardian-java")
    public void consume(AnalysisResult result) {
        log.debug("Received analysis result from Kafka: messageId={}", result.getMessageId());
        onResult(result);
    }

    @Override
    public void onResult(AnalysisResult result) {
        log.info("Analysis result: messageId={}, tenantId={}, flagged={}, reasons={}, confidence={}",
            result.getMessageId(),
            result.getTenantId(),
            result.isFlagged(),
            result.getFlagReasons(),
            result.getConfidence());

        if (result.getActionTaken() != null && !result.getActionTaken().isEmpty()) {
            log.info("Action taken: {} for messageId={}", result.getActionTaken(), result.getMessageId());
        }
    }
}
