package com.streamguardian.messaging;

import com.streamguardian.ingest.dto.AnalysisResult;

/**
 * Interface for consuming analysis results from the analysis service.
 * Implementations can use Kafka, RabbitMQ, Redis, etc.
 */
public interface ResultConsumer {

    /**
     * Handle an analysis result received from the messaging system
     *
     * @param result the analysis result to handle
     */
    void onResult(AnalysisResult result);
}
