package io.kestra.plugin.scripts.exec.scripts.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.runners.TaskRunnerDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.net.URI;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class ScriptOutput implements Output {
    @Schema(
        title = "The values extracted from executed `commands` using the [Kestra outputs](https://kestra.io/docs/scripts/outputs-metrics#outputs-and-metrics-in-script-and-commands-tasks) format."
    )
    @JsonInclude(JsonInclude.Include.ALWAYS) // always include vars so it's easier to reason about in expressions
    private final Map<String, Object> vars;

    @Schema(
        title = "The exit code of the entire flow execution."
    )
    @NotNull
    private final int exitCode;

    @Schema(
        title = "The output files' URIs in Kestra's internal storage."
    )
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> outputFiles;

    @JsonIgnore
    private final int stdOutLineCount;

    @JsonIgnore
    private final int stdErrLineCount;

    private TaskRunnerDetailResult taskRunner;

}
