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
import java.util.HashMap;
import java.util.Map;

/**
 * Cancel a running Flink job, optionally triggering a savepoint.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Cancel a Flink job",
    description = "Cancel a running Flink job, with optional savepoint creation before cancellation."
)
@Plugin(
    examples = {
        @Example(
            title = "Cancel a job without savepoint",
            code = """
                id: cancel-flink-job
                type: io.kestra.plugin.flink.Cancel
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "{{ outputs.submit-job.jobId }}"
                """
        ),
        @Example(
            title = "Cancel a job with savepoint",
            code = """
                id: cancel-with-savepoint
                type: io.kestra.plugin.flink.Cancel
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "a12b34c56d78e90f12g34h56i78j90k12"
                withSavepoint: true
                savepointDir: "s3://my-bucket/savepoints/cancel-{{ execution.id }}"
                """
        )
    }
)
public class Cancel extends Task implements RunnableTask<Cancel.Output> {

    @Schema(
        title = "Flink connection configuration"
    )
    @PluginProperty
    @NotNull
    private FlinkConnection connection;

    @Schema(
        title = "Job ID",
        description = "The unique identifier of the job to cancel"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Create savepoint before cancellation",
        description = "Whether to trigger a savepoint before cancelling the job"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> withSavepoint = Property.of(false);

    @Schema(
        title = "Savepoint directory",
        description = "Directory where to store the savepoint (required if withSavepoint is true)"
    )
    @PluginProperty(dynamic = true)
    private Property<String> savepointDir;

    @Schema(
        title = "Drain the job",
        description = "Whether to drain the job (process all pending records) before cancellation"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> drain = Property.of(false);

    @Override
    public Output run(RunContext runContext) throws Exception {
        FlinkClient client = FlinkClient.from(runContext, connection);
        
        String jobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        Boolean withSavepoint = runContext.render(this.withSavepoint).as(Boolean.class).orElse(false);
        Boolean drain = runContext.render(this.drain).as(Boolean.class).orElse(false);
        String savepointDir = runContext.render(this.savepointDir).as(String.class).orElse(null);
        
        runContext.logger().info("Cancelling Flink job {} (savepoint: {}, drain: {})", 
            jobId, withSavepoint, drain);
        
        String savepointPath = null;
        
        if (withSavepoint) {
            if (savepointDir == null) {
                throw new IllegalArgumentException("savepointDir is required when withSavepoint is true");
            }
            
            // First trigger a savepoint
            runContext.logger().info("Triggering savepoint for job {} to directory {}", jobId, savepointDir);
            savepointPath = triggerSavepoint(client, jobId, savepointDir);
            runContext.logger().info("Savepoint created at: {}", savepointPath);
        }
        
        // Cancel the job
        String cancelResponse;
        if (drain) {
            // Use stop API for graceful shutdown
            cancelResponse = client.patch("/jobs/" + jobId + "/stop", "{}");
        } else {
            // Use cancel API for immediate cancellation
            cancelResponse = client.patch("/jobs/" + jobId, "{}");
        }
        
        runContext.logger().info("Job {} cancellation initiated", jobId);
        
        return Output.builder()
            .jobId(jobId)
            .cancelled(true)
            .savepointPath(savepointPath)
            .method(drain ? "STOP" : "CANCEL")
            .build();
    }
    
    private String triggerSavepoint(FlinkClient client, String jobId, String savepointDir) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("target-directory", savepointDir);
        payload.put("cancel-job", false); // Don't cancel, just create savepoint
        
        ObjectMapper mapper = new ObjectMapper();
        String response = client.post("/jobs/" + jobId + "/savepoints", 
            mapper.writeValueAsString(payload));
        
        // Parse response to get savepoint trigger ID
        Map<String, Object> responseMap = mapper.readValue(response, Map.class);
        String triggerId = (String) responseMap.get("request-id");
        
        // Wait for savepoint completion
        return waitForSavepoint(client, jobId, triggerId);
    }
    
    private String waitForSavepoint(FlinkClient client, String jobId, String triggerId) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        for (int i = 0; i < 60; i++) { // Wait up to 10 minutes
            String response = client.get("/jobs/" + jobId + "/savepoints/" + triggerId);
            Map<String, Object> status = mapper.readValue(response, Map.class);
            
            String statusValue = (String) status.get("status");
            if ("COMPLETED".equals(statusValue)) {
                Map<String, Object> operation = (Map<String, Object>) status.get("operation");
                if (operation != null) {
                    return (String) operation.get("location");
                }
            } else if ("FAILED".equals(statusValue)) {
                String failureCause = (String) status.get("failure-cause");
                throw new FlinkException("Savepoint failed: " + failureCause);
            }
            
            Thread.sleep(10000); // Wait 10 seconds before next check
        }
        
        throw new FlinkException("Savepoint did not complete within timeout");
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Job ID",
            description = "The unique identifier of the cancelled job"
        )
        private String jobId;
        
        @Schema(
            title = "Cancelled",
            description = "Whether the job was successfully cancelled"
        )
        private Boolean cancelled;
        
        @Schema(
            title = "Savepoint path",
            description = "Path to the savepoint created before cancellation (if any)"
        )
        private String savepointPath;
        
        @Schema(
            title = "Cancellation method",
            description = "The method used for cancellation (CANCEL or STOP)"
        )
        private String method;
    }
}