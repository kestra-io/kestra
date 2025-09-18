package io.kestra.plugin.flink;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.flink.models.JobState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Monitor a Flink job until it reaches a terminal state or times out.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Monitor a Flink job",
    description = "Wait for a Flink job to reach a terminal state (FINISHED, FAILED, or CANCELED) or timeout."
)
@Plugin(
    examples = {
        @Example(
            title = "Monitor a job with timeout",
            code = """
                id: monitor-flink-job
                type: io.kestra.plugin.flink.MonitorJob
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "{{ outputs.submit-job.jobId }}"
                waitTimeout: PT30M
                """
        ),
        @Example(
            title = "Monitor a job with custom polling interval",
            code = """
                id: monitor-flink-job-custom
                type: io.kestra.plugin.flink.MonitorJob
                connection:
                  url: "http://flink-jobmanager:8081"
                jobId: "a12b34c56d78e90f12g34h56i78j90k12"
                waitTimeout: PT1H
                pollInterval: PT30S
                """
        )
    }
)
public class MonitorJob extends Task implements RunnableTask<MonitorJob.Output> {

    @Schema(
        title = "Flink connection configuration"
    )
    @PluginProperty
    @NotNull
    private FlinkConnection connection;

    @Schema(
        title = "Job ID",
        description = "The unique identifier of the job to monitor"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jobId;

    @Schema(
        title = "Wait timeout",
        description = "Maximum time to wait for the job to complete"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Duration> waitTimeout = Property.of(Duration.ofMinutes(30));

    @Schema(
        title = "Poll interval",
        description = "How often to check the job status"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Duration> pollInterval = Property.of(Duration.ofSeconds(10));

    @Schema(
        title = "Fail on job failure",
        description = "Whether to throw an exception if the job fails"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> failOnJobFailure = Property.of(true);

    @Override
    public Output run(RunContext runContext) throws Exception {
        FlinkClient client = FlinkClient.from(runContext, connection);
        
        String jobId = runContext.render(this.jobId).as(String.class).orElseThrow();
        Duration timeout = runContext.render(this.waitTimeout).as(Duration.class).orElse(Duration.ofMinutes(30));
        Duration pollInterval = runContext.render(this.pollInterval).as(Duration.class).orElse(Duration.ofSeconds(10));
        Boolean failOnFailure = runContext.render(this.failOnJobFailure).as(Boolean.class).orElse(true);
        
        runContext.logger().info("Monitoring Flink job {} with timeout {}", jobId, timeout);
        
        Instant startTime = Instant.now();
        Instant endTime = startTime.plus(timeout);
        JobState finalState = null;
        Map<String, Object> jobDetails = null;
        
        while (Instant.now().isBefore(endTime)) {
            try {
                // Get job details
                String response = client.get("/jobs/" + jobId);
                ObjectMapper mapper = new ObjectMapper();
                jobDetails = mapper.readValue(response, Map.class);
                
                String stateStr = (String) jobDetails.get("state");
                JobState currentState = JobState.valueOf(stateStr.toUpperCase());
                
                runContext.logger().debug("Job {} current state: {}", jobId, currentState);
                
                if (currentState.isTerminal()) {
                    finalState = currentState;
                    runContext.logger().info("Job {} reached terminal state: {}", jobId, finalState);
                    break;
                }
                
                // Sleep before next poll
                Thread.sleep(pollInterval.toMillis());
                
            } catch (Exception e) {
                runContext.logger().error("Error checking job status: {}", e.getMessage());
                Thread.sleep(pollInterval.toMillis());
            }
        }
        
        String exitCode;
        if (finalState == null) {
            exitCode = "TIMEOUT";
            runContext.logger().warn("Job {} monitoring timed out after {}", jobId, timeout);
        } else if (finalState.isSuccessful()) {
            exitCode = "SUCCESS";
        } else if (finalState.isFailed()) {
            exitCode = "FAILED";
            if (failOnFailure) {
                throw new FlinkException("Job " + jobId + " failed with state: " + finalState);
            }
        } else {
            exitCode = finalState.name();
        }
        
        Duration actualDuration = Duration.between(startTime, Instant.now());
        
        return Output.builder()
            .jobId(jobId)
            .finalState(finalState != null ? finalState.name() : "UNKNOWN")
            .exitCode(exitCode)
            .duration(actualDuration)
            .jobDetails(jobDetails)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Job ID",
            description = "The unique identifier of the monitored job"
        )
        private String jobId;
        
        @Schema(
            title = "Final state",
            description = "The final state of the job when monitoring ended"
        )
        private String finalState;
        
        @Schema(
            title = "Exit code",
            description = "The exit code indicating the result (SUCCESS, FAILED, TIMEOUT, etc.)"
        )
        private String exitCode;
        
        @Schema(
            title = "Duration",
            description = "How long the monitoring took"
        )
        private Duration duration;
        
        @Schema(
            title = "Job details",
            description = "Detailed information about the job from Flink"
        )
        private Map<String, Object> jobDetails;
    }
}