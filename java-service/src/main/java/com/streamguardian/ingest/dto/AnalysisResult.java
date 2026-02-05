package com.streamguardian.ingest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * DTO for analysis results from Python LangGraph pipeline
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisResult {

    @JsonProperty("message_id")
    private String messageId;

    @JsonProperty("tenant_id")
    private String tenantId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("original_text")
    private String originalText;

    @JsonProperty("is_flagged")
    private boolean flagged;

    @JsonProperty("flag_reasons")
    private List<String> flagReasons;

    @JsonProperty("confidence")
    private double confidence;

    @JsonProperty("action_taken")
    private String actionTaken;

    @JsonProperty("action_result")
    private Map<String, Object> actionResult;

    @JsonProperty("model_name")
    private String modelName;
}
