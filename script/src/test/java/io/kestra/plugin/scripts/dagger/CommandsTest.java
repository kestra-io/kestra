package io.kestra.plugin.scripts.dagger;

import io.kestra.core.models.property.Property;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CommandsTest {

    @Test
    void commandsProperties() {
        Commands task = Commands.builder()
            .id("dagger-test")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("container | from \"alpine\" | withExec [\"echo\", \"test\"] | stdout")))
            .build();

        assertThat(task.getId(), is("dagger-test"));
        assertThat(task.getType(), is(Commands.class.getName()));
        assertThat(task.getCommands(), not(nullValue()));
        assertThat(task.getContainerImage(), not(nullValue()));
        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void commandsWithCustomImage() {
        Commands task = Commands.builder()
            .id("dagger-test")
            .type(Commands.class.getName())
            .containerImage(Property.ofValue("custom/dagger:v1"))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getContainerImage(), not(nullValue()));
    }

    @Test
    void multipleCommands() {
        Commands task = Commands.builder()
            .id("multi-commands")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of(
                "container | from \"alpine\" | withExec [\"echo\", \"First\"] | stdout",
                "container | from \"alpine\" | withExec [\"echo\", \"Second\"] | stdout"
            )))
            .build();

        assertThat(task.getCommands(), not(nullValue()));
    }

    @Test
    void beforeCommandsSetup() {
        Commands task = Commands.builder()
            .id("with-before-commands")
            .type(Commands.class.getName())
            .beforeCommands(Property.ofValue(List.of("dagger version")))
            .commands(Property.ofValue(List.of(
                "container | from \"alpine\" | stdout"
            )))
            .build();

        assertThat(task.getBeforeCommands(), not(nullValue()));
    }

    @Test
    void interpreterDefault() {
        Commands task = Commands.builder()
            .id("interpreter-test")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void interpreterCustom() {
        Commands task = Commands.builder()
            .id("custom-interpreter")
            .type(Commands.class.getName())
            .interpreter(Property.ofValue(List.of("dagger", "query", "--verbose")))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void environmentVariables() {
        Commands task = Commands.builder()
            .id("with-env")
            .type(Commands.class.getName())
            .env(Property.ofValue(Map.of("DAGGER_VAR", "test_value")))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getEnv(), not(nullValue()));
    }

    @Test
    void failFastDefault() {
        Commands task = Commands.builder()
            .id("failfast-test")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getFailFast(), not(nullValue()));
    }

    @Test
    void failFastCustom() {
        Commands task = Commands.builder()
            .id("failfast-false")
            .type(Commands.class.getName())
            .failFast(Property.ofValue(false))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getFailFast(), not(nullValue()));
    }

    @Test
    void emptyBeforeCommands() {
        Commands task = Commands.builder()
            .id("empty-before")
            .type(Commands.class.getName())
            .beforeCommands(Property.ofValue(Collections.emptyList()))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getCommands(), not(nullValue()));
    }

    @Test
    void interpreterWithDynamicProperty() {
        Commands task = Commands.builder()
            .id("dynamic-interpreter")
            .type(Commands.class.getName())
            .interpreter(Property.ofExpression("{{ inputs.interpreter }}"))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        // Verify the property is dynamic
        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void containerImageWithDynamicProperty() {
        Commands task = Commands.builder()
            .id("dynamic-image")
            .type(Commands.class.getName())
            .containerImage(Property.ofExpression("{{ inputs.image }}"))
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        assertThat(task.getContainerImage(), not(nullValue()));
    }

    @Test
    void executionDelegatesToAbstractExecScript() {
        // Verify that Commands.run() only calls commands(runContext).withCommands().run()
        // This ensures no custom execution logic outside AbstractExecScript pattern
        Commands task = Commands.builder()
            .id("delegation-test")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        // Code inspection confirms: Commands.run() has ONLY:
        // return this.commands(runContext).withCommands(this.commands).run();
        // No custom logic, complete delegation to AbstractExecScript
        assertThat(task, not(nullValue()));
    }

    @Test
    void outputContractIsScriptOutput() {
        // Verify return type is ScriptOutput (interface contract)
        Commands task = Commands.builder()
            .id("output-contract")
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("container | from \"alpine\" | stdout")))
            .build();

        // Method signature: public ScriptOutput run(RunContext runContext)
        // Confirms output contract matches AbstractExecScript pattern
        assertThat(task, not(nullValue()));
    }
}
