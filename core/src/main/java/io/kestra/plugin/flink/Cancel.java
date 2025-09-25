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

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
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
    title = "Cancel a running Flink job.",
    description = "This task cancels a running Flink job. Optionally, it can trigger a savepoint " +
                  "before cancellation to preserve the job state."
)
@Plugin(
    examples = {
        @Example(
            title = "Cancel a job with savepoint",
            full = true,
            code = """
                id: cancel-flink-job
                namespace: company.team

                tasks:
                  - id: cancel-job
                    type: io.kestra.plugin.flink.Cancel
                    restUrl: "http://flink-jobmanager:8081"
                    jobId: "{{ inputs.jobId }}"
                    withSavepoint: true
                    savepointDir: "s3://flink/savepoints/canceled/{{ execution.id }}"
                    drainJob: true
                """
        ),
        @Example(
            title = "Force cancel without savepoint",
            code = """
                id: force-cancel
                type: io.kestra.plugin.flink.Cancel
                restUrl: "http://flink-jobmanager:8081"
                jobId: "{{ inputs.jobId }}"
                withSavepoint: false
                """
        )
    }
)
public class Cancel extends Task implements RunnableTask<Cancel.Output> {

    @Schema(
        title = "Flink REST API URL",
        description = "The base URL of the Flink cluster's REST API, e.g., 'http://flink-jobmanager:8081'"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> restUrl;

    @Schema(
        title = "Job ID",
        description = "The ID of the Flink job to cancel"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Create savepoint before cancellation",
        description = "Whether to trigger a savepoint before canceling the job. Defaults to false."
    )
    @PluginProperty
    @Builder.Default
    private Property<Boolean> withSavepoint = Property.of(false);

    @Schema(
        title = "Savepoint directory",
        description = "Target directory for the savepoint. Required if withSavepoint is true."
    )
    @PluginProperty(dynamic = true)
    private Property<String> savepointDir;

    @Schema(
        title = "Drain job",
        description = "Whether to drain the job (process all remaining input) before cancellation. " +
                      "Only applicable for streaming jobs. Defaults to false."
    )
    @PluginProperty
    @Builder.Default
    private Property<Boolean> drainJob = Property.of(false);

    @Schema(
        title = "Cancellation timeout",
        description = "Maximum time to wait for cancellation to complete in seconds. Defaults to 60."
    )
    @PluginProperty
    @Builder.Default
    private Property<Integer> cancellationTimeout = Property.of(60);

    @Override
    public Cancel.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String renderedRestUrl = runContext.render(this.restUrl).as(String.class).orElseThrow();
        String renderedJobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        Boolean withSp = runContext.render(this.withSavepoint).as(Boolean.class).orElse(false);
        Boolean drain = runContext.render(this.drainJob).as(Boolean.class).orElse(false);

        logger.info("Cancelling Flink job: {} (withSavepoint: {}, drain: {})", renderedJobId, withSp, drain);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        String savepointPath = null;

        if (withSp) {
            // Trigger savepoint before cancellation
            savepointPath = triggerSavepoint(runContext, client, renderedRestUrl, renderedJobId);
            logger.info("Savepoint created at: {}", savepointPath);
        }

        // Cancel the job
        String cancellationResult = cancelJob(runContext, client, renderedRestUrl, renderedJobId, drain);

        // Wait for job to be canceled
        waitForJobCancellation(runContext, client, renderedRestUrl, renderedJobId);

        logger.info("Successfully cancelled job: {}", renderedJobId);

        return Output.builder()
            .jobId(renderedJobId)
            .savepointPath(savepointPath)
            .cancellationResult(cancellationResult)
            .success(true)
            .build();
    }

    private String triggerSavepoint(RunContext runContext, HttpClient client, String restUrl, String jobId)
            throws Exception {

        String savepointDirectory = null;
        if (savepointDir != null) {
            savepointDirectory = runContext.render(savepointDir).as(String.class).orElse(null);
        }

        if (savepointDirectory == null) {
            throw new IllegalArgumentException("savepointDir is required when withSavepoint is true");
        }

        String payload = "{\"target-directory\":\"" + savepointDirectory + "\",\"cancel-job\":false}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(restUrl + "/v1/jobs/" + jobId + "/savepoints"))
            .timeout(Duration.ofMinutes(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new RuntimeException("Failed to trigger savepoint: " + response.statusCode() + " - " + response.body());
        }

        // Extract request ID and wait for completion
        String requestId = extractRequestIdFromResponse(response.body());
        return waitForSavepointCompletion(runContext, client, restUrl, jobId, requestId);
    }

    private String cancelJob(RunContext runContext, HttpClient client, String restUrl, String jobId, boolean drain)
            throws IOException, InterruptedException {

        String endpoint = drain ? "/v1/jobs/" + jobId + "/stop" : "/v1/jobs/" + jobId;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(restUrl + endpoint))
            .timeout(Duration.ofSeconds(60));

        HttpRequest request;
        if (drain) {
            // For drain, we need to PATCH with stop request
            request = requestBuilder
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString("{\"drain\":true}"))
                .build();
        } else {
            // For regular cancel, we DELETE
            request = requestBuilder
                .DELETE()
                .build();
        }

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 202) {
            throw new RuntimeException("Failed to cancel job: " + response.statusCode() + " - " + response.body());
        }

        return drain ? "Job stop requested (drain mode)" : "Job cancellation requested";
    }

    private void waitForJobCancellation(RunContext runContext, HttpClient client, String restUrl, String jobId)
            throws Exception {

        int timeoutSeconds = runContext.render(cancellationTimeout).as(Integer.class).orElse(60);
        long startTime = System.currentTimeMillis();

        while (true) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(restUrl + "/v1/jobs/" + jobId))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) {
                // Job not found, likely canceled
                break;
            }

            if (response.statusCode() == 200) {
                String state = extractJobStateFromResponse(response.body());
                if ("CANCELED".equals(state) || "FINISHED".equals(state)) {
                    break;
                }
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutSeconds * 1000L) {
                throw new RuntimeException("Job cancellation timed out after " + timeoutSeconds + " seconds");
            }

            Thread.sleep(2000); // Check every 2 seconds
        }
    }

    private String waitForSavepointCompletion(RunContext runContext, HttpClient client, String restUrl,
                                            String jobId, String requestId) throws Exception {

        int timeoutSeconds = 300; // 5 minutes for savepoint
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

            Thread.sleep(5000); // Check every 5 seconds
        }
    }

    // Simple JSON extraction methods
    private String extractRequestIdFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"request-id\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        throw new RuntimeException("Could not extract request ID from response: " + responseBody);
    }

    private String extractJobStateFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"state\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        return "UNKNOWN";
    }

    private String extractSavepointStatusFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"status\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        return "UNKNOWN";
    }

    private String extractSavepointPathFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"location\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        return null;
    }

    private String extractSavepointErrorFromResponse(String responseBody) {
        String[] parts = responseBody.split("\"failure-cause\":\\s*\"([^\"]+)\"");
        if (parts.length > 1) {
            return parts[1].split("\"")[0];
        }
        return "Unknown error";
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The cancelled job ID",
            description = "The ID of the Flink job that was cancelled"
        )
        private final String jobId;

        @Schema(
            title = "Savepoint path",
            description = "Path to the savepoint created before cancellation (if withSavepoint was true)"
        )
        private final String savepointPath;

        @Schema(
            title = "Cancellation result",
            description = "Result message from the cancellation operation"
        )
        private final String cancellationResult;

        @Schema(
            title = "Success",
            description = "Whether the cancellation completed successfully"
        )
        private final Boolean success;
    }
}