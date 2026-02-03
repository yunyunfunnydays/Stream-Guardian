package com.streamguardian.ingest.service;

import com.streamguardian.ingest.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Service for forwarding chat messages to Python AI service.
 */
@Service
@Slf4j
public class MessageForwardingService {

    private final RestClient restClient;
    private final String pythonServiceUrl;

    public MessageForwardingService(
            RestClient.Builder restClientBuilder,
            @Value("${python-service.url:http://python-service:8000}") String pythonServiceUrl) {
        this.pythonServiceUrl = pythonServiceUrl;
        this.restClient = restClientBuilder
                .baseUrl(pythonServiceUrl)
                .build();
        log.info("Message forwarding service initialized with Python URL: {}", pythonServiceUrl);
    }

    /**
     * Forward a chat message to the Python service for AI analysis
     */
    public void forwardToPythonService(ChatMessage message) {
        String analyzeUrl = "/api/analyze";

        log.info("→ Forwarding message to Python service: {}{}", pythonServiceUrl, analyzeUrl);

        try {
            Map<String, Object> response = restClient.post()
                    .uri(analyzeUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .body(Map.class);

            log.info("← Python service response: {}", response);

        } catch (Exception e) {
            log.error("Failed to forward message to Python service: {}", e.getMessage());
            // In production, you might want to implement retry logic or queue the message
        }
    }
}
