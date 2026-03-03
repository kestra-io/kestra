package io.kestra.plugin.scripts.dagger;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.scripts.exec.AbstractExecScript;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Execute a Dagger script via the Dagger CLI.",
    description = """
        Execute a Dagger script using the official Dagger CLI.
        
        The script uses Dagger's query syntax and can contain multiple pipeline operations. The Dagger CLI must be installed in the execution environment.
        
        The task will fail if the script returns a non-zero exit code."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a simple Dagger script.",
            full = true,
            code = """
                id: dagger_script_example
                namespace: company.team
                
                tasks:
                  - id: run_dagger_script
                    type: io.kestra.plugin.scripts.dagger.Script
                    script: |
                      container |
                      from "alpine" |
                      withExec ["echo", "Hello from Dagger!"] |
                      stdout
                """
        ),
        @Example(
            title = "Execute a Dagger script with file operations.",
            code = """
                id: dagger_file_script
                type: io.kestra.plugin.scripts.dagger.Script
                script: |
                  container |
                  from "alpine" |
                  withExec ["cat", "/etc/os-release"] |
                  stdout
                """
        ),
        @Example(
            title = "Execute a Dagger script with custom container image.",
            code = """
                id: dagger_python_version
                type: io.kestra.plugin.scripts.dagger.Script
                containerImage: python:3.11
                script: |
                  container |
                  from "python:3.11" |
                  withExec ["python", "--version"] |
                  stdout
                """
        ),
        @Example(
            title = "Execute Dagger script with beforeCommands to verify installation.",
            code = """
                id: dagger_with_verification
                type: io.kestra.plugin.scripts.dagger.Script
                beforeCommands:
                  - dagger version
                script: |
                  container |
                  from "node:20" |
                  withExec ["node", "--version"] |
                  stdout
                """
        )
    }
)
public class Script extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    
    @Schema(
        title = "The Dagger script to execute.",
        description = "A Dagger script using the Dagger query syntax. The script can contain multiple pipeline operations separated by newlines or pipes."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    private Property<String> script;

    @Schema(
        title = "The container image to use for the Dagger CLI.",
        description = "Docker image containing the Dagger CLI. Only used when running with a container-based task runner."
    )
    @Builder.Default
    private Property<String> containerImage = Property.ofValue("dagger/dagger:latest");

    @Builder.Default
    @Schema(
        title = "Which interpreter to use.",
        description = "The Dagger CLI command used to execute queries. Defaults to 'dagger query'."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected Property<List<String>> interpreter = Property.ofValue(List.of("dagger", "query"));

    @Override
    public Property<String> getContainerImage() {
        return this.containerImage;
    }

    @Override
    public ScriptOutput run(RunContext runContext) throws Exception {
        // Convert the script string into a list with a single element for CommandsWrapper
        String renderedScript = runContext.render(this.script).as(String.class).orElseThrow(
            () -> new IllegalArgumentException("Script cannot be null or empty")
        );
        
        return this.commands(runContext)
            .withCommands(Property.ofValue(List.of(renderedScript)))
            .run();
    }
}
