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
    title = "Execute Dagger commands via the Dagger CLI.",
    description = """
        Execute one or more Dagger commands using the official Dagger CLI.
        
        Commands are executed using Dagger's query syntax. The Dagger CLI must be installed in the execution environment.
        
        Each command will be executed sequentially, and the task will fail if any command returns a non-zero exit code (unless `failFast` is set to `false`)."""
)
@Plugin(
    examples = {
        @Example(
            title = "Execute a simple Dagger command.",
            full = true,
            code = """
                id: dagger_hello_world
                namespace: company.team
                
                tasks:
                  - id: run_dagger
                    type: io.kestra.plugin.scripts.dagger.Commands
                    commands:
                      - container | from "alpine" | withExec ["echo", "Hello from Dagger!"] | stdout
                """
        ),
        @Example(
            title = "Execute multiple Dagger commands.",
            code = """
                id: run_multiple_commands
                type: io.kestra.plugin.scripts.dagger.Commands
                commands:
                  - container | from "alpine" | withExec ["echo", "First command"] | stdout
                  - container | from "ubuntu" | withExec ["echo", "Second command"] | stdout
                """
        ),
        @Example(
            title = "Execute Dagger commands with custom container image.",
            code = """
                id: dagger_custom_image
                type: io.kestra.plugin.scripts.dagger.Commands
                containerImage: alpine:latest
                commands:
                  - container | from "python:3.11" | withExec ["python", "--version"] | stdout
                """
        ),
        @Example(
            title = "Execute Dagger command with beforeCommands to set up environment.",
            code = """
                id: dagger_with_setup
                type: io.kestra.plugin.scripts.dagger.Commands
                beforeCommands:
                  - dagger version
                commands:
                  - container | from "alpine" | withExec ["cat", "/etc/os-release"] | stdout
                """
        )
    }
)
public class Commands extends AbstractExecScript implements RunnableTask<ScriptOutput> {
    
    @Schema(
        title = "The Dagger commands to execute.",
        description = "A list of Dagger commands using the Dagger query syntax. Each command will be executed sequentially."
    )
    @PluginProperty(dynamic = true)
    @NotNull
    @NotEmpty
    private Property<List<String>> commands;

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
        return this.commands(runContext)
            .withCommands(this.commands)
            .run();
    }
}
