package com.streamguardian.mcp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool Definition following the MCP specification
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class McpToolDefinition {

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("inputSchema")
    private InputSchema inputSchema;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InputSchema {
        @JsonProperty("type")
        private String type = "object";

        @JsonProperty("properties")
        private Map<String, PropertyDefinition> properties;

        @JsonProperty("required")
        private List<String> required;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PropertyDefinition {
        @JsonProperty("type")
        private String type;

        @JsonProperty("description")
        private String description;
    }
}
