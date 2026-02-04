package com.streamguardian.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamguardian.ingest.handler.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending moderation action notifications to WebSocket clients.
 * Simulates Twitch API responses being sent back through WebSocket.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ToolExecutionNotificationService {

    private final ChatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    /**
     * Send bot reply message to the channel (simulating Twitch bot message)
     * This is called when reply_chat tool is executed
     */
    public void notifyBotMessage(String tenantId, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "bot_message");
            notification.put("message", message);

            String jsonMessage = objectMapper.writeValueAsString(notification);

            log.info("========== Sending bot message to {}: {}", tenantId, message);
            webSocketHandler.broadcastToTenant(tenantId, jsonMessage);

        } catch (Exception e) {
            log.error("========== Failed to send bot message: {}", e.getMessage(), e);
        }
    }
}
