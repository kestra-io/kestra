package io.kestra.plugin.flink;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a savepoint for a running Flink job.",
    description = "This task triggers a savepoint for a running Flink job without canceling it. " +
                  "Savepoints are used to capture the state of a job for backup or migration purposes."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger savepoint with specific directory",
            full = true,
            code = """
                id: create-savepoint
                namespace: company.team

                tasks:
                  - id: trigger-savepoint
                    type: io.kestra.plugin.flink.TriggerSavepoint
                    restUrl: "http://flink-jobmanager:8081"
                    jobId: "{{ inputs.jobId }}"
                    targetDirectory: "s3://flink/savepoints/backup/{{ execution.id }}"
                    savepointTimeout: 300
                """
        ),
        @Example(
            title = "Trigger savepoint with default directory",
            code = """
                id: default-savepoint
                type: io.kestra.plugin.flink.TriggerSavepoint
                restUrl: "http://flink-jobmanager:8081"
                jobId: "{{ inputs.jobId }}"
                """
        )
    }
)
public class TriggerSavepoint extends Task implements RunnableTask<TriggerSavepoint.Output> {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Schema(
        title = "Flink REST API URL",
        description = "The base URL of the Flink cluster's REST API, e.g., 'http://flink-jobmanager:8081'"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> restUrl;

    @Schema(
        title = "Job ID",
        description = "The ID of the Flink job to create a savepoint for"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Target directory",
        description = "Target directory for the savepoint. If not specified, " +
                      "the cluster's default savepoint directory will be used."
    )
    @PluginProperty(dynamic = true)
    private Property<String> targetDirectory;

    @Schema(
        title = "Cancel job after savepoint",
        description = "Whether to cancel the job after creating the savepoint. Defaults to false."
    )
    @PluginProperty
    @Builder.Default
    private Property<Boolean> cancelJob = Property.of(false);

    @Schema(
        title = "Savepoint timeout",
        description = "Maximum time to wait for savepoint creation in seconds. Defaults to 300."
    )
    @PluginProperty
    @Builder.Default
    private Property<Integer> savepointTimeout = Property.of(300);

    @Schema(
        title = "Format type",
        description = "Format type of the savepoint. Can be 'CANONICAL' or 'NATIVE'. " +
                      "Defaults to 'CANONICAL' for better compatibility."
    )
    @PluginProperty
    @Builder.Default
    private Property<String> formatType = Property.of("CANONICAL");

    @Override
    public TriggerSavepoint.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String renderedRestUrl = runContext.render(this.restUrl).as(String.class).orElseThrow();
        String renderedJobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        Boolean cancel = runContext.render(this.cancelJob).as(Boolean.class).orElse(false);

        logger.info("Triggering savepoint for job: {} (cancel: {})", renderedJobId, cancel);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        // Trigger the savepoint
        String requestId = triggerSavepoint(runContext, client, renderedRestUrl, renderedJobId, cancel);

        // Wait for completion
        String savepointPath = waitForSavepointCompletion(runContext, client, renderedRestUrl, renderedJobId, requestId);

        logger.info("Savepoint created successfully at: {}", savepointPath);

        return Output.builder()
            .jobId(renderedJobId)
            .savepointPath(savepointPath)
            .requestId(requestId)
            .success(true)
            .build();
    }

    private String triggerSavepoint(RunContext runContext, HttpClient client, String restUrl, String jobId, boolean cancelJob)
            throws Exception {

        StringBuilder payload = new StringBuilder();
        payload.append("{");

        if (targetDirectory != null) {
            String directory = runContext.render(targetDirectory).as(String.class).orElse(null);
            if (directory != null) {
                payload.append("\"target-directory\":\"").append(directory).append("\"");
            }
        }

        String format = runContext.render(formatType).as(String.class).orElse("CANONICAL");
        if (payload.length() > 1) {
            payload.append(",");
        }
        payload.append("\"format-type\":\"").append(format).append("\"");

        payload.append(",\"cancel-job\":").append(cancelJob);

        payload.append("}");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(restUrl + "/v1/jobs/" + jobId + "/savepoints"))
            .timeout(Duration.ofMinutes(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new RuntimeException("Failed to trigger savepoint: " + response.statusCode() + " - " + response.body());
        }

        // Extract and return request ID
        return extractRequestIdFromResponse(response.body());
    }

    private String waitForSavepointCompletion(RunContext runContext, HttpClient client, String restUrl,
                                            String jobId, String requestId) throws Exception {

        int timeoutSeconds = runContext.render(savepointTimeout).as(Integer.class).orElse(300);
        long startTime = System.currentTimeMillis();

        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(restUrl + "/v1/jobs/" + jobId + "/savepoints/" + requestId))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to check savepoint status: " + response.statusCode() + " - " + response.body());
            }

            String status = extractSavepointStatusFromResponse(response.body());
            runContext.logger().debug("Savepoint status: {}", status);

            if ("COMPLETED".equals(status)) {
                return extractSavepointPathFromResponse(response.body());
            } else if ("FAILED".equals(status)) {
                String error = extractSavepointErrorFromResponse(response.body());
                throw new RuntimeException("Savepoint failed: " + error);
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) {
                throw new RuntimeException("Savepoint timed out after " + timeoutSeconds + " seconds");
            }

            // Wait before next check
            Thread.sleep(5000); // Check every 5 seconds
        }
    }

    private String extractRequestIdFromResponse(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode requestId = root.path("request-id");
            if (requestId.isTextual()) {
                return requestId.asText();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse savepoint trigger response: " + responseBody, e);
        }
        throw new RuntimeException("Could not extract request ID from response: " + responseBody);
    }

    private String extractSavepointStatusFromResponse(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode status = root.path("status").path("id");
            if (status.isTextual()) {
                return status.asText();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse savepoint status response: " + responseBody, e);
        }
        return "UNKNOWN";
    }

    private String extractSavepointPathFromResponse(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode location = root.path("operation").path("location");
            if (location.isTextual()) {
                return location.asText();
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse savepoint status response: " + responseBody, e);
        }
        throw new RuntimeException("Could not extract savepoint path from response: " + responseBody);
    }

    private String extractSavepointErrorFromResponse(String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode failure = root.path("operation").path("failure-cause");
            if (failure.isObject()) {
                JsonNode message = failure.path("message");
                if (message.isTextual()) {
                    return message.asText();
                }
                JsonNode className = failure.path("class-name");
                if (className.isTextual()) {
                    return className.asText();
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse savepoint status response: " + responseBody, e);
        }
        return "Unknown error";
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The job ID",
            description = "The ID of the Flink job for which the savepoint was created"
        )
        private final String jobId;

        @Schema(
            title = "Savepoint path",
            description = "Path to the created savepoint"
        )
        private final String savepointPath;

        @Schema(
            title = "Request ID",
            description = "The savepoint request ID"
        )
        private final String requestId;

        @Schema(
            title = "Success",
            description = "Whether the savepoint was created successfully"
        )
        private final Boolean success;
    }
}