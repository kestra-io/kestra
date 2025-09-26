package io.kestra.plugin.flink;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Monitor a Flink job until it reaches a terminal state.",
    description = "This task monitors a Flink job and waits for it to reach a terminal state " +
                  "(FINISHED, CANCELED, FAILED) or until the specified timeout is reached."
)
@Plugin(
    examples = {
        @Example(
            title = "Monitor a job with timeout",
            full = true,
            code = """
                id: monitor-flink-job
                namespace: company.team

                tasks:
                  - id: submit-job
                    type: io.kestra.plugin.flink.Submit
                    restUrl: "http://flink-jobmanager:8081"
                    jarUri: "s3://flink/jars/my-job.jar"
                    entryClass: "com.example.Main"

                  - id: monitor-job
                    type: io.kestra.plugin.flink.MonitorJob
                    restUrl: "http://flink-jobmanager:8081"
                    jobId: "{{ outputs.submit-job.jobId }}"
                    waitTimeout: "PT30M"
                    checkInterval: "PT30S"
                """
        ),
        @Example(
            title = "Monitor job with early termination on failure",
            code = """
                id: monitor-with-failure-check
                type: io.kestra.plugin.flink.MonitorJob
                restUrl: "http://flink-jobmanager:8081"
                jobId: "{{ inputs.jobId }}"
                waitTimeout: "PT1H"
                failOnError: true
                """
        )
    }
)
public class MonitorJob extends Task implements RunnableTask<MonitorJob.Output> {

    @Schema(
        title = "Flink REST API URL",
        description = "The base URL of the Flink cluster's REST API, e.g., 'http://flink-jobmanager:8081'"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> restUrl;

    @Schema(
        title = "Job ID",
        description = "The ID of the Flink job to monitor"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Wait timeout",
        description = "Maximum time to wait for the job to complete. " +
                      "Use ISO-8601 duration format (e.g., 'PT30M' for 30 minutes). " +
                      "Defaults to PT10M."
    )
    @PluginProperty
    @Builder.Default
    private Property<Duration> waitTimeout = Property.of(Duration.parse("PT10M"));

    @Schema(
        title = "Check interval",
        description = "Interval between job status checks. " +
                      "Use ISO-8601 duration format (e.g., 'PT30S' for 30 seconds). " +
                      "Defaults to PT10S."
    )
    @PluginProperty
    @Builder.Default
    private Property<Duration> checkInterval = Property.of(Duration.parse("PT10S"));

    @Schema(
        title = "Fail on error",
        description = "Whether to fail the task if the job reaches FAILED state. " +
                      "If false, the task will complete successfully even if the job failed. " +
                      "Defaults to true."
    )
    @PluginProperty
    @Builder.Default
    private Property<Boolean> failOnError = Property.of(true);

    @Schema(
        title = "Expected terminal states",
        description = "List of job states to consider as successful completion. " +
                      "Defaults to ['FINISHED']."
    )
    @PluginProperty
    private Property<java.util.List<String>> expectedTerminalStates;

    @Override
    public MonitorJob.Output run(RunContext runContext) throws Exception {
        Logger logger = runContext.logger();

        String renderedRestUrl = runContext.render(this.restUrl).as(String.class).orElseThrow();
        String renderedJobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        Duration timeout = runContext.render(this.waitTimeout).as(Duration.class).orElse(Duration.parse("PT10M"));
        Duration interval = runContext.render(this.checkInterval).as(Duration.class).orElse(Duration.parse("PT10S"));
        Boolean failOnErr = runContext.render(this.failOnError).as(Boolean.class).orElse(true);

        logger.info("Monitoring Flink job: {} with timeout: {}", renderedJobId, timeout);

        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeout.toMillis();

        String finalState = null;
        String finalStateDetails = null;
        long duration = 0;

        while (true) {
            JobStatus status = getJobStatus(client, renderedRestUrl, renderedJobId);

            logger.info("Job {} status: {}", renderedJobId, status.getState());

            if (isTerminalState(status.getState())) {
                finalState = status.getState();
                finalStateDetails = status.getStateDetails();
                duration = System.currentTimeMillis() - startTime;

                // Check if this is a successful completion
                java.util.List<String> expectedStates = getExpectedTerminalStates(runContext);
                boolean isSuccess = expectedStates.contains(status.getState());

                if (!isSuccess && failOnErr && "FAILED".equals(status.getState())) {
                    throw new RuntimeException("Job " + renderedJobId + " failed: " + finalStateDetails);
                }

                break;
            }

            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMillis) {
                duration = timeoutMillis;
                finalState = "TIMEOUT";
                finalStateDetails = "Job monitoring timed out after " + timeout;
                logger.warn("Job monitoring timed out after {}", timeout);
                break;
            }

            // Wait before next check
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Monitoring was interrupted", e);
            }
        }

        logger.info("Job {} monitoring completed. Final state: {} (duration: {}ms)",
            renderedJobId, finalState, duration);

        return Output.builder()
            .jobId(renderedJobId)
            .finalState(finalState)
            .stateDetails(finalStateDetails)
            .duration(Duration.ofMillis(duration))
            .success(!"FAILED".equals(finalState) && !"TIMEOUT".equals(finalState))
            .build();
    }

    private JobStatus getJobStatus(HttpClient client, String restUrl, String jobId)
            throws IOException, InterruptedException {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(restUrl + "/v1/jobs/" + jobId))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            throw new RuntimeException("Job not found: " + jobId);
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to get job status: " + response.statusCode() + " - " + response.body());
        }

        return parseJobStatus(response.body());
    }

    private JobStatus parseJobStatus(String responseBody) {
        // Simple JSON parsing - in production, would use proper JSON parser
        String state = extractJsonValue(responseBody, "state");
        String stateDetails = extractJsonValue(responseBody, "state-details");

        return JobStatus.builder()
            .state(state)
            .stateDetails(stateDetails)
            .build();
    }

    private String extractJsonValue(String json, String key) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"")
            .matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private boolean isTerminalState(String state) {
        return "FINISHED".equals(state) ||
               "CANCELED".equals(state) ||
               "FAILED".equals(state);
    }

    private java.util.List<String> getExpectedTerminalStates(RunContext runContext)
            throws IllegalVariableEvaluationException {
        if (expectedTerminalStates != null) {
            return runContext.render(expectedTerminalStates).asList(String.class);
        }
        return java.util.Arrays.asList("FINISHED");
    }

    @Builder
    @Getter
    public static class JobStatus {
        private final String state;
        private final String stateDetails;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The monitored job ID",
            description = "The ID of the Flink job that was monitored"
        )
        private final String jobId;

        @Schema(
            title = "Final state",
            description = "The final state of the job (FINISHED, FAILED, CANCELED, or TIMEOUT)"
        )
        private final String finalState;

        @Schema(
            title = "State details",
            description = "Additional details about the job state"
        )
        private final String stateDetails;

        @Schema(
            title = "Monitoring duration",
            description = "Total time spent monitoring the job"
        )
        private final Duration duration;

        @Schema(
            title = "Success",
            description = "Whether the job completed successfully"
        )
        private final Boolean success;
    }
}