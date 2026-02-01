package com.streamguardian.ingest.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamguardian.ingest.dto.ChatMessage;
import com.streamguardian.ingest.service.MessageForwardingService;
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
    private final MessageForwardingService forwardingService;

    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        activeSessions.put(sessionId, session);
        log.info("WebSocket connected: {} (total active: {})", sessionId, activeSessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);

        try {
            ChatMessage chatMessage = objectMapper.readValue(payload, ChatMessage.class);

            // Add timestamp if not present
            if (chatMessage.getTimestamp() == null) {
                chatMessage.setTimestamp(System.currentTimeMillis());
            }

            log.info("📨 Chat message received:");
            log.info("   ├─ Tenant  : {}", chatMessage.getTenantId());
            log.info("   ├─ User    : {} ({})", chatMessage.getUsername(), chatMessage.getUserId());
            log.info("   └─ Message : {}", chatMessage.getText());

            // Forward to Python service for analysis
            forwardingService.forwardToPythonService(chatMessage);

        } catch (Exception e) {
            log.error("Failed to process chat message: {}", e.getMessage(), e);
            session.sendMessage(new TextMessage("{\"error\": \"" + e.getMessage() + "\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        activeSessions.remove(sessionId);
        log.info("WebSocket disconnected: {} - {} (remaining: {})",
                sessionId, status.getReason(), activeSessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session {}: {}",
                session.getId(), exception.getMessage());
        activeSessions.remove(session.getId());
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}
