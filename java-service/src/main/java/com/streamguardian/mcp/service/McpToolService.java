package com.streamguardian.mcp.service;

import com.streamguardian.mcp.dto.McpToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for handling MCP tool definitions and executions.
 * Simulates Twitch API actions with structured logging.
 */
@Service
@Slf4j
public class McpToolService {

    /**
     * Get all available MCP tools for Stream Guardian
     */
    public List<McpToolDefinition> getToolDefinitions() {
        return List.of(
                buildBanUserTool(),
                buildTimeoutUserTool(),
                buildReplyChatTool()
        );
    }

    private McpToolDefinition buildBanUserTool() {
        Map<String, McpToolDefinition.PropertyDefinition> properties = new LinkedHashMap<>();
        properties.put("tenant_id", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The tenant/channel identifier")
                .build());
        properties.put("user_id", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The user ID to ban")
                .build());
        properties.put("reason", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The reason for the ban")
                .build());

        return McpToolDefinition.builder()
                .name("ban_user")
                .description("Permanently ban a user from the chat. Use this for severe violations like harassment, hate speech, or spam bots.")
                .inputSchema(McpToolDefinition.InputSchema.builder()
                        .type("object")
                        .properties(properties)
                        .required(List.of("tenant_id", "user_id", "reason"))
                        .build())
                .build();
    }

    private McpToolDefinition buildTimeoutUserTool() {
        Map<String, McpToolDefinition.PropertyDefinition> properties = new LinkedHashMap<>();
        properties.put("tenant_id", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The tenant/channel identifier")
                .build());
        properties.put("user_id", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The user ID to timeout")
                .build());
        properties.put("duration", McpToolDefinition.PropertyDefinition.builder()
                .type("integer")
                .description("Timeout duration in seconds (e.g., 60, 300, 600)")
                .build());

        return McpToolDefinition.builder()
                .name("timeout_user")
                .description("Temporarily timeout a user from the chat. Use this for minor violations or warnings.")
                .inputSchema(McpToolDefinition.InputSchema.builder()
                        .type("object")
                        .properties(properties)
                        .required(List.of("tenant_id", "user_id", "duration"))
                        .build())
                .build();
    }

    private McpToolDefinition buildReplyChatTool() {
        Map<String, McpToolDefinition.PropertyDefinition> properties = new LinkedHashMap<>();
        properties.put("tenant_id", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The tenant/channel identifier")
                .build());
        properties.put("message", McpToolDefinition.PropertyDefinition.builder()
                .type("string")
                .description("The message to send to the chat")
                .build());

        return McpToolDefinition.builder()
                .name("reply_chat")
                .description("Send a message to the chat as the bot. Use this to warn users, provide information, or engage with the community.")
                .inputSchema(McpToolDefinition.InputSchema.builder()
                        .type("object")
                        .properties(properties)
                        .required(List.of("tenant_id", "message"))
                        .build())
                .build();
    }

    /**
     * Execute a tool and return the result
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) {
        log.info("═══════════════════════════════════════════════════════════════");
        log.info("🔧 MCP TOOL EXECUTION: {}", toolName);
        log.info("───────────────────────────────────────────────────────────────");

        return switch (toolName) {
            case "ban_user" -> executeBanUser(arguments);
            case "timeout_user" -> executeTimeoutUser(arguments);
            case "reply_chat" -> executeReplyChat(arguments);
            default -> {
                log.error("❌ Unknown tool: {}", toolName);
                yield Map.of(
                        "success", false,
                        "error", "Unknown tool: " + toolName
                );
            }
        };
    }

    private Map<String, Object> executeBanUser(Map<String, Object> args) {
        String tenantId = (String) args.get("tenant_id");
        String userId = (String) args.get("user_id");
        String reason = (String) args.get("reason");

        log.info("🚫 BAN_USER Action:");
        log.info("   ├─ Tenant ID : {}", tenantId);
        log.info("   ├─ User ID   : {}", userId);
        log.info("   └─ Reason    : {}", reason);
        log.info("📡 [SIMULATED] Twitch API: POST /moderation/bans");
        log.info("═══════════════════════════════════════════════════════════════");

        return Map.of(
                "success", true,
                "action", "ban_user",
                "tenant_id", tenantId,
                "user_id", userId,
                "reason", reason,
                "message", String.format("User %s has been banned from channel %s", userId, tenantId)
        );
    }

    private Map<String, Object> executeTimeoutUser(Map<String, Object> args) {
        String tenantId = (String) args.get("tenant_id");
        String userId = (String) args.get("user_id");
        Object durationObj = args.get("duration");
        int duration = durationObj instanceof Number ? ((Number) durationObj).intValue() : 60;

        log.info("⏱️ TIMEOUT_USER Action:");
        log.info("   ├─ Tenant ID : {}", tenantId);
        log.info("   ├─ User ID   : {}", userId);
        log.info("   └─ Duration  : {} seconds", duration);
        log.info("📡 [SIMULATED] Twitch API: POST /moderation/bans (with duration)");
        log.info("═══════════════════════════════════════════════════════════════");

        return Map.of(
                "success", true,
                "action", "timeout_user",
                "tenant_id", tenantId,
                "user_id", userId,
                "duration", duration,
                "message", String.format("User %s has been timed out for %d seconds in channel %s", userId, duration, tenantId)
        );
    }

    private Map<String, Object> executeReplyChat(Map<String, Object> args) {
        String tenantId = (String) args.get("tenant_id");
        String message = (String) args.get("message");

        log.info("💬 REPLY_CHAT Action:");
        log.info("   ├─ Tenant ID : {}", tenantId);
        log.info("   └─ Message   : {}", message);
        log.info("📡 [SIMULATED] Twitch API: POST /chat/messages");
        log.info("═══════════════════════════════════════════════════════════════");

        return Map.of(
                "success", true,
                "action", "reply_chat",
                "tenant_id", tenantId,
                "message", message,
                "result", String.format("Message sent to channel %s: %s", tenantId, message)
        );
    }
}
