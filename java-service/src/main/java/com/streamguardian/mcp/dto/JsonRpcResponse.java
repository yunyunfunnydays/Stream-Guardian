package com.streamguardian.mcp.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * JSON-RPC 2.0 Response object for MCP protocol
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcResponse {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("id")
    private Object id;

    @JsonProperty("result")
    private Object result;

    @JsonProperty("error")
    private JsonRpcError error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JsonRpcError {
        private int code;
        private String message;
        private Object data;
    }

    public static JsonRpcResponse success(Object id, Object result) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .result(result)
                .build();
    }

    public static JsonRpcResponse error(Object id, int code, String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(id)
                .error(JsonRpcError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
}
