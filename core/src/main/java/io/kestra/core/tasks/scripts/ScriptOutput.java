package io.kestra.core.tasks.scripts;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotNull;

@Builder
@Getter
public class ScriptOutput implements io.kestra.core.models.tasks.Output {
    @Schema(
        title = "The value extract from output of the commands"
    )
    private final Map<String, Object> vars;

    @Schema(
        title = "The standard output line count"
    )
    private final int stdOutLineCount;

    @Schema(
        title = "The standard error line count"
    )
    private final int stdErrLineCount;

    @Schema(
        title = "The exit code of the whole execution"
    )
    @NotNull
    private final int exitCode;

    @Schema(
        title = "Deprecated output files",
        description = "use `outputFiles`",
        deprecated = true
    )
    @Deprecated
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> files;

    @Schema(
        title = "The output files uri in Kestra internal storage"
    )
    @PluginProperty(additionalProperties = URI.class)
    private final Map<String, URI> outputFiles;

    @JsonIgnore
    private Boolean warningOnStdErr;

    @Override
    public Optional<State.Type> finalState() {
        return this.warningOnStdErr != null && this.stdErrLineCount > 0 ? Optional.of(State.Type.WARNING) : Output.super.finalState();
    }
}
