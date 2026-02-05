package com.streamguardian.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamguardian.ingest.dto.ChatMessage;
import com.streamguardian.messaging.MessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * WebSocket handler for receiving simulated Twitch chat messages.
 * Forwards messages to Python service for AI analysis.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final MessageProducer messageProducer;

    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    // Map tenant ID to session ID (one tenant can have one or more sessions)
    private final Map<String, String> tenantToSession = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("========== WebSocket connected: {} (total active: {})", sessionId, activeSessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("========== Received WebSocket message: {}", payload);

        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);

            // Add timestamp if not present
            if (chatMessage.getTimestamp() == null) {
                chatMessage.setTimestamp(System.currentTimeMillis());
            }

            // Track which tenant this session belongs to
            String sessionId = session.getId();
            String tenantId = chatMessage.getTenantId();
            tenantToSession.put(tenantId, sessionId);

            // Send to messaging system for Python service analysis
            messageProducer.send(chatMessage);

        } catch (Exception e) {
            log.error("========== Failed to process chat message: {}", e.getMessage(), e);
            session.sendMessage(new TextMessage("{\"error\": \"" + e.getMessage() + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);

        // Remove from tenant mapping
        tenantToSession.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));

        log.info("========== WebSocket disconnected: {} - {} (remaining: {})",
                sessionId, status.getReason(), activeSessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("========== WebSocket transport error for session {}: {}",
                session.getId(), exception.getMessage());
        String sessionId = session.getId();
        activeSessions.remove(sessionId);

        // Remove from tenant mapping
        tenantToSession.entrySet().removeIf(entry -> entry.getValue().equals(sessionId));
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Broadcast a message to the WebSocket client connected to a specific tenant/channel
     */
    public void broadcastToTenant(String tenantId, String message) {
        String sessionId = tenantToSession.get(tenantId);
        if (sessionId == null) {
            log.debug("========== No active session found for tenant: {}", tenantId);
            return;
        }

        WebSocketSession session = activeSessions.get(sessionId);
        if (session == null || !session.isOpen()) {
            log.debug("========== Session {} for tenant {} is not open", sessionId, tenantId);
            return;
        }

        try {
            session.sendMessage(new TextMessage(message));
            log.debug("========== Broadcasted message to tenant {} (session: {})", tenantId, sessionId);
        } catch (Exception e) {
            log.error("========== Failed to broadcast to tenant {}: {}", tenantId, e.getMessage());
        }
    }
}
