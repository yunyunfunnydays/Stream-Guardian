package com.streamguardian.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

/**
 * JSON-RPC 2.0 Request object for MCP protocol
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JsonRpcRequest {

    @JsonProperty("jsonrpc")
    private String jsonrpc = "2.0";

    @JsonProperty("id")
    private Object id;

    @JsonProperty("method")
    private String method;

    @JsonProperty("params")
    private Map<String, Object> params;
}
