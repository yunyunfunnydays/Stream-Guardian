package com.streamguardian.mcp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing MCP SSE connections.
 * Implements the SSE transport layer of the Model Context Protocol.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class McpSseService {

    private final ObjectMapper objectMapper;

    // Store active SSE connections by session ID
    private final Map<String, SseEmitter> activeConnections = new ConcurrentHashMap<>();

    /**
     * Create a new SSE connection and return the emitter
     */
    public SseEmitter createConnection() {
        String sessionId = UUID.randomUUID().toString();

        // SSE timeout: 0 means no timeout (keep connection open indefinitely)
        SseEmitter emitter = new SseEmitter(0L);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed: {}", sessionId);
            activeConnections.remove(sessionId);
        });

        emitter.onTimeout(() -> {
            log.info("SSE connection timed out: {}", sessionId);
            activeConnections.remove(sessionId);
        });

        emitter.onError(e -> {
            log.error("SSE connection error for session {}: {}", sessionId, e.getMessage());
            activeConnections.remove(sessionId);
        });

        activeConnections.put(sessionId, emitter);

        // Send initial endpoint event with the session ID
        try {
            String endpointData = objectMapper.writeValueAsString(Map.of(
                    "endpoint", "/mcp/messages?sessionId=" + sessionId
            ));
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data(endpointData));
            log.info("SSE connection established: {} (total active: {})", sessionId, activeConnections.size());
        } catch (IOException e) {
            log.error("Failed to send endpoint event: {}", e.getMessage());
            activeConnections.remove(sessionId);
        }

        return emitter;
    }

    /**
     * Send a message to a specific SSE session
     */
    public void sendMessage(String sessionId, Object message) {
        SseEmitter emitter = activeConnections.get(sessionId);
        if (emitter != null) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(jsonMessage));
                log.debug("Sent SSE message to session {}: {}", sessionId, jsonMessage);
            } catch (IOException e) {
                log.error("Failed to send SSE message to session {}: {}", sessionId, e.getMessage());
                activeConnections.remove(sessionId);
            }
        } else {
            log.warn("No active SSE connection for session: {}", sessionId);
        }
    }

    /**
     * Broadcast a message to all active SSE connections
     */
    public void broadcastMessage(Object message) {
        String jsonMessage;
        try {
            jsonMessage = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize broadcast message: {}", e.getMessage());
            return;
        }

        activeConnections.forEach((sessionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(jsonMessage));
            } catch (IOException e) {
                log.error("Failed to broadcast to session {}: {}", sessionId, e.getMessage());
                activeConnections.remove(sessionId);
            }
        });
    }

    /**
     * Check if a session exists
     */
    public boolean sessionExists(String sessionId) {
        return activeConnections.containsKey(sessionId);
    }

    /**
     * Get the count of active connections
     */
    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
}
