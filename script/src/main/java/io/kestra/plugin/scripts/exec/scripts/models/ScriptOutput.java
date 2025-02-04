package io.kestra.plugin.scripts.exec.scripts.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.kestra.core.models.tasks.runners.TaskRunnerDetailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;

@Builder
@Getter
public class ScriptOutput implements Output {
    @Schema(
        title = "The value extracted from the output of the executed `commands`."
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

    @JsonIgnore
    private Boolean warningOnStdErr;

    private TaskRunnerDetailResult taskRunner;

    @Override
    public Optional<State.Type> finalState() {
        return this.warningOnStdErr != null && this.warningOnStdErr && this.stdErrLineCount > 0 ? Optional.of(State.Type.WARNING) : Output.super.finalState();
    }
}
