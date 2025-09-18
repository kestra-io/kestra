package io.kestra.plugin.flink.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

/**
 * Result of submitting a job to Flink.
 */
@Builder
@Getter
@Jacksonized
public class JobSubmissionResult {
    
    @JsonProperty("jobid")
    private String jobId;
    
    @JsonProperty("filename")
    private String filename;
    
    private String status;
    
    public static JobSubmissionResult fromResponse(String response) {
        // Parse the JSON response from Flink REST API
        // Example response: {"jobid":"a12b34c56d78e90f12g34h56i78j90k12"}
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(response, JobSubmissionResult.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse job submission result: " + response, e);
        }
    }
}