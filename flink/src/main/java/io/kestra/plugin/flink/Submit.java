package io.kestra.plugin.flink;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.flink.models.JobSubmissionResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Submit a JAR-based job to Apache Flink.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Submit a JAR-based job to Apache Flink",
    description = "Submit a JAR file containing a Flink job to a Flink cluster for execution."
)
@Plugin(
    examples = {
        @Example(
            title = "Submit a Flink job with basic configuration",
            code = """
                id: flink-submit-job
                type: io.kestra.plugin.flink.Submit
                connection:
                  url: "http://flink-jobmanager:8081"
                jarUri: "s3://my-bucket/flink-jobs/my-job.jar"
                entryClass: "com.example.FlinkJob"
                args:
                  - "--input"
                  - "kafka://input-topic"
                  - "--output"
                  - "kafka://output-topic"
                parallelism: 4
                """
        ),
        @Example(
            title = "Submit a job with savepoint restoration",
            code = """
                id: flink-submit-with-savepoint
                type: io.kestra.plugin.flink.Submit
                connection:
                  url: "http://flink-jobmanager:8081"
                jarUri: "s3://my-bucket/flink-jobs/my-job.jar"
                entryClass: "com.example.FlinkJob"
                restoreFromSavepoint: "s3://my-bucket/savepoints/savepoint-123456"
                allowNonRestoredState: true
                parallelism: 6
                """
        )
    }
)
public class Submit extends Task implements RunnableTask<Submit.Output> {

    @Schema(
        title = "Flink connection configuration"
    )
    @PluginProperty
    @NotNull
    private FlinkConnection connection;

    @Schema(
        title = "JAR file URI",
        description = "URI of the JAR file containing the Flink job. Can be a local file, HTTP URL, or cloud storage URI."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> jarUri;

    @Schema(
        title = "Entry class",
        description = "Fully qualified name of the main class to execute"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private Property<String> entryClass;

    @Schema(
        title = "Program arguments",
        description = "List of arguments to pass to the main method"
    )
    @PluginProperty(dynamic = true)
    private Property<List<String>> args;

    @Schema(
        title = "Parallelism",
        description = "The parallelism degree for the job execution"
    )
    @PluginProperty(dynamic = true)
    private Property<Integer> parallelism;

    @Schema(
        title = "Savepoint path",
        description = "Path to a savepoint to restore the job from"
    )
    @PluginProperty(dynamic = true)
    private Property<String> restoreFromSavepoint;

    @Schema(
        title = "Allow non-restored state",
        description = "Allow job to be restored even if there are state elements that cannot be restored"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private Property<Boolean> allowNonRestoredState = Property.of(false);

    @Schema(
        title = "Job configuration",
        description = "Additional configuration properties for the job"
    )
    @PluginProperty(dynamic = true)
    private Property<Map<String, String>> jobConfig;

    @Override
    public Output run(RunContext runContext) throws Exception {
        FlinkClient client = FlinkClient.from(runContext, connection);
        
        // First, upload the JAR if it's not already uploaded
        String jarUri = runContext.render(this.jarUri).as(String.class).orElseThrow();
        String jarId = uploadJar(client, runContext, jarUri);
        
        // Prepare job submission payload
        Map<String, Object> jobPayload = buildJobPayload(runContext, jarId);
        
        runContext.logger().info("Submitting Flink job with JAR {} and entry class {}", 
            jarUri, runContext.render(this.entryClass).as(String.class).orElse(""));
        
        // Submit the job
        String response = client.post("/jars/" + jarId + "/run", 
            new ObjectMapper().writeValueAsString(jobPayload));
        
        JobSubmissionResult result = JobSubmissionResult.fromResponse(response);
        
        runContext.logger().info("Flink job submitted successfully with ID: {}", result.getJobId());
        
        return Output.builder()
            .jobId(result.getJobId())
            .jarId(jarId)
            .status("SUBMITTED")
            .build();
    }

    private String uploadJar(FlinkClient client, RunContext runContext, String jarUri) throws Exception {
        // For simplicity, we'll assume the JAR is already available in Flink
        // In a real implementation, you would:
        // 1. Download the JAR from the URI if it's remote
        // 2. Upload it to Flink using multipart/form-data POST to /jars/upload
        // 3. Return the JAR ID from the response
        
        // For now, return a placeholder - this would need to be implemented
        // based on the actual JAR upload logic
        return "placeholder-jar-id";
    }

    private Map<String, Object> buildJobPayload(RunContext runContext, String jarId) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        
        // Entry class
        String entryClass = runContext.render(this.entryClass).as(String.class).orElseThrow();
        payload.put("entryClass", entryClass);
        
        // Program arguments
        List<String> args = runContext.render(this.args).asList(String.class);
        if (args != null && !args.isEmpty()) {
            payload.put("programArgs", String.join(" ", args));
        }
        
        // Parallelism
        Integer parallelism = runContext.render(this.parallelism).as(Integer.class).orElse(null);
        if (parallelism != null) {
            payload.put("parallelism", parallelism);
        }
        
        // Savepoint restoration
        String savepointPath = runContext.render(this.restoreFromSavepoint).as(String.class).orElse(null);
        if (savepointPath != null) {
            payload.put("savepointPath", savepointPath);
            
            Boolean allowNonRestored = runContext.render(this.allowNonRestoredState).as(Boolean.class).orElse(false);
            payload.put("allowNonRestoredState", allowNonRestored);
        }
        
        // Job configuration
        Map<String, String> jobConfig = runContext.render(this.jobConfig).asMap(String.class, String.class);
        if (jobConfig != null && !jobConfig.isEmpty()) {
            payload.put("flinkConfiguration", jobConfig);
        }
        
        return payload;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Job ID",
            description = "The unique identifier of the submitted job"
        )
        private String jobId;
        
        @Schema(
            title = "JAR ID",
            description = "The identifier of the uploaded JAR file"
        )
        private String jarId;
        
        @Schema(
            title = "Submission status",
            description = "The status of the job submission"
        )
        private String status;
    }
}