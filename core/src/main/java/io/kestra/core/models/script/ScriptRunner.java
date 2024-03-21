package io.kestra.core.models.script;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.runners.RunContext;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Base class for all script runners.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
public abstract class ScriptRunner {
    @NotBlank
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;

    /**
     * This method will be called by the script plugin to run a script on a script runner.
     * Script runners may be local or remote.
     * For local script runner (like in process or in a local Docker engine), <code>filesToUpload</code> and <code>filesToDownload</code> may be ignored as they car directoy used the task working directory.
     * For remote script runner (like Kubernetes or in a cloud provider), <code>filesToUpload</code> must be used to upload input and namespace files to the runner,
     * and <code>filesToDownload</code> must be used to download output files from the runner.
     */
    public abstract RunnerResult run(RunContext runContext, ScriptCommands commands, List<String> filesToUpload, List<String> filesToDownload) throws Exception;
}
