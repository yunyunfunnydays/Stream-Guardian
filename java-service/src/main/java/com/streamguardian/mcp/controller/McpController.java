package com.streamguardian.mcp.controller;

import com.streamguardian.mcp.dto.JsonRpcRequest;
import com.streamguardian.mcp.dto.JsonRpcResponse;
import com.streamguardian.mcp.dto.McpToolDefinition;
import com.streamguardian.mcp.service.McpSseService;
import com.streamguardian.mcp.service.McpToolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Server Controller implementing SSE transport.
 *
 * Endpoints:
 * - GET /mcp/sse: Establish SSE connection
 * - POST /mcp/messages: Handle JSON-RPC requests
 */
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@Slf4j
public class McpController {

    private final McpSseService sseService;
    private final McpToolService toolService;

    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "stream-guardian";
    private static final String SERVER_VERSION = "1.0.0";

    /**
     * GET /mcp/sse - Establish SSE connection for MCP
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter establishSseConnection() {
        log.info("New MCP SSE connection request");
        return sseService.createConnection();
    }

    /**
     * POST /mcp/messages - Handle JSON-RPC requests from MCP clients
     */
    @PostMapping(value = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonRpcResponse> handleMessage(
            @RequestParam(required = false) String sessionId,
            @RequestBody JsonRpcRequest request) {

        log.info("MCP Request - Method: {}, ID: {}", request.getMethod(), request.getId());

        JsonRpcResponse response = processRequest(request);

        // Also send response via SSE if session exists
        if (sessionId != null && sseService.sessionExists(sessionId)) {
            sseService.sendMessage(sessionId, response);
        }

        return ResponseEntity.ok(response);
    }

    private JsonRpcResponse processRequest(JsonRpcRequest request) {
        return switch (request.getMethod()) {
            case "initialize" -> handleInitialize(request);
            case "initialized" -> handleInitialized(request);
            case "tools/list" -> handleToolsList(request);
            case "tools/call" -> handleToolsCall(request);
            case "ping" -> handlePing(request);
            default -> JsonRpcResponse.error(request.getId(), -32601, "Method not found: " + request.getMethod());
        };
    }

    /**
     * Handle 'initialize' request - First message from client
     */
    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        log.info("MCP Initialize request received");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);

        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools", Map.of("listChanged", true));
        result.put("capabilities", capabilities);

        log.info("MCP Server initialized - Protocol: {}, Server: {} v{}",
                MCP_PROTOCOL_VERSION, SERVER_NAME, SERVER_VERSION);

        return JsonRpcResponse.success(request.getId(), result);
    }

    /**
     * Handle 'initialized' notification - Client acknowledges initialization
     */
    private JsonRpcResponse handleInitialized(JsonRpcRequest request) {
        log.info("MCP Client initialized notification received");
        // This is a notification, return empty success
        return JsonRpcResponse.success(request.getId(), Map.of());
    }

    /**
     * Handle 'tools/list' request - Return available tools
     */
    private JsonRpcResponse handleToolsList(JsonRpcRequest request) {
        log.info("MCP Tools list request received");

        List<McpToolDefinition> tools = toolService.getToolDefinitions();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);

        log.info("Returning {} tools: {}", tools.size(),
                tools.stream().map(McpToolDefinition::getName).toList());

        return JsonRpcResponse.success(request.getId(), result);
    }

    /**
     * Handle 'tools/call' request - Execute a tool
     */
    @SuppressWarnings("unchecked")
    private JsonRpcResponse handleToolsCall(JsonRpcRequest request) {
        Map<String, Object> params = request.getParams();
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.getOrDefault("arguments", Map.of());

        log.info("MCP Tool call: {} with arguments: {}", toolName, arguments);

        try {
            Map<String, Object> toolResult = toolService.executeTool(toolName, arguments);

            // Format response according to MCP spec
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of(
                    "type", "text",
                    "text", toolResult.toString()
            )));
            result.put("isError", !Boolean.TRUE.equals(toolResult.get("success")));

            return JsonRpcResponse.success(request.getId(), result);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage(), e);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("content", List.of(Map.of(
                    "type", "text",
                    "text", "Error: " + e.getMessage()
            )));
            result.put("isError", true);

            return JsonRpcResponse.success(request.getId(), result);
        }
    }

    /**
     * Handle 'ping' request - Health check
     */
    private JsonRpcResponse handlePing(JsonRpcRequest request) {
        log.debug("MCP Ping received");
        return JsonRpcResponse.success(request.getId(), Map.of());
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "server", SERVER_NAME,
                "version", SERVER_VERSION,
                "activeConnections", sseService.getActiveConnectionCount()
        ));
    }
}
