package io.kestra.plugin.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Trigger a savepoint for a running Flink job.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a savepoint for a Flink job",
    description = "Create a savepoint for a running Flink job to capture the current state."
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger a savepoint",
            code = """
                id: trigger-savepoint
                type: io.kestra.plugin.flink.TriggerSavepoint
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "{{ outputs.submit-job.jobId }}"
                targetDirectory: "s3://my-bucket/savepoints/manual-{{ execution.id }}"
                """
        ),
        @Example(
            title = "Trigger savepoint with cancellation",
            code = """
                id: savepoint-and-cancel
                type: io.kestra.plugin.flink.TriggerSavepoint
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "a12b34c56d78e90f12g34h56i78j90k12"
                targetDirectory: "s3://my-bucket/savepoints/shutdown-{{ execution.id }}"
                cancelJob: true
                timeout: PT10M
                """
        )
    }
)
public class TriggerSavepoint extends Task implements RunnableTask<TriggerSavepoint.Output> {

    @Schema(
        title = "Flink connection configuration"
    )
    @PluginProperty
    @NotNull
    private FlinkConnection connection;

    @Schema(
        title = "Job ID",
        description = "The unique identifier of the job to create a savepoint for"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Target directory",
        description = "Directory where the savepoint should be stored"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> targetDirectory;

    @Schema(
        title = "Cancel job after savepoint",
        description = "Whether to cancel the job after the savepoint is completed"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> cancelJob = Property.of(false);

    @Schema(
        title = "Timeout",
        description = "Maximum time to wait for the savepoint to complete"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Duration> timeout = Property.of(Duration.ofMinutes(10));

    @Schema(
        title = "Format type",
        description = "Savepoint format type (CANONICAL, NATIVE)"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<String> formatType = Property.of("CANONICAL");

    @Override
    public Output run(RunContext runContext) throws Exception {
        FlinkClient client = FlinkClient.from(runContext, connection);
        
        String jobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        String targetDirectory = runContext.render(this.targetDirectory).as(String.class).orElseThrow();
        Boolean cancelJob = runContext.render(this.cancelJob).as(Boolean.class).orElse(false);
        Duration timeout = runContext.render(this.timeout).as(Duration.class).orElse(Duration.ofMinutes(10));
        String formatType = runContext.render(this.formatType).as(String.class).orElse("CANONICAL");
        
        runContext.logger().info("Triggering savepoint for job {} to directory {} (cancel: {})", 
            jobId, targetDirectory, cancelJob);
        
        Instant startTime = Instant.now();
        
        // Prepare savepoint request
        Map<String, Object> payload = new HashMap<>();
        payload.put("target-directory", targetDirectory);
        payload.put("cancel-job", cancelJob);
        payload.put("format-type", formatType);
        
        ObjectMapper mapper = new ObjectMapper();
        String response = client.post("/jobs/" + jobId + "/savepoints", 
            mapper.writeValueAsString(payload));
        
        // Parse response to get savepoint trigger ID
        Map<String, Object> responseMap = mapper.readValue(response, Map.class);
        String triggerId = (String) responseMap.get("request-id");
        
        runContext.logger().info("Savepoint trigger ID: {}", triggerId);
        
        // Wait for savepoint completion
        String savepointPath = null;
        String status = "IN_PROGRESS";
        
        Instant endTime = startTime.plus(timeout);
        while (Instant.now().isBefore(endTime)) {
            try {
                String statusResponse = client.get("/jobs/" + jobId + "/savepoints/" + triggerId);
                Map<String, Object> statusMap = mapper.readValue(statusResponse, Map.class);
                
                status = (String) statusMap.get("status");
                
                if ("COMPLETED".equals(status)) {
                    Map<String, Object> operation = (Map<String, Object>) statusMap.get("operation");
                    if (operation != null) {
                        savepointPath = (String) operation.get("location");
                    }
                    runContext.logger().info("Savepoint completed successfully at: {}", savepointPath);
                    break;
                } else if ("FAILED".equals(status)) {
                    String failureCause = (String) statusMap.get("failure-cause");
                    throw new FlinkException("Savepoint failed: " + failureCause);
                }
                
                // Wait before next check
                Thread.sleep(5000);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FlinkException("Savepoint monitoring was interrupted");
            }
        }
        
        if (!"COMPLETED".equals(status)) {
            throw new FlinkException("Savepoint did not complete within timeout of " + timeout);
        }
        
        Duration actualDuration = Duration.between(startTime, Instant.now());
        
        return Output.builder()
            .jobId(jobId)
            .triggerId(triggerId)
            .savepointPath(savepointPath)
            .status(status)
            .cancelJob(cancelJob)
            .duration(actualDuration)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Job ID",
            description = "The unique identifier of the job"
        )
        private String jobId;
        
        @Schema(
            title = "Trigger ID",
            description = "The unique identifier for the savepoint trigger request"
        )
        private String triggerId;
        
        @Schema(
            title = "Savepoint path",
            description = "Path where the savepoint was created"
        )
        private String savepointPath;
        
        @Schema(
            title = "Status",
            description = "Final status of the savepoint operation"
        )
        private String status;
        
        @Schema(
            title = "Job cancelled",
            description = "Whether the job was cancelled after the savepoint"
        )
        private Boolean cancelJob;
        
        @Schema(
            title = "Duration",
            description = "How long the savepoint operation took"
        )
        private Duration duration;
    }
}