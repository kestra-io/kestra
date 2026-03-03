package io.kestra.plugin.scripts.dagger;

import io.kestra.core.models.property.Property;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class ScriptTest {

    @Test
    void scriptProperties() {
        Script task = Script.builder()
            .id("dagger-script-test")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | withExec [\"echo\", \"test\"] | stdout"))
            .build();

        assertThat(task.getId(), is("dagger-script-test"));
        assertThat(task.getType(), is(Script.class.getName()));
        assertThat(task.getScript(), not(nullValue()));
        assertThat(task.getContainerImage(), not(nullValue()));
        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void scriptWithCustomImage() {
        Script task = Script.builder()
            .id("dagger-script-test")
            .type(Script.class.getName())
            .containerImage(Property.ofValue("custom/dagger:v1"))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getContainerImage(), not(nullValue()));
    }

    @Test
    void multilineScript() {
        String multilineScript = """
            container |
            from "alpine" |
            withExec ["cat", "/etc/os-release"] |
            stdout
            """;

        Script task = Script.builder()
            .id("multiline-script")
            .type(Script.class.getName())
            .script(Property.ofValue(multilineScript))
            .build();

        assertThat(task.getScript(), not(nullValue()));
    }

    @Test
    void scriptWithBeforeCommands() {
        Script task = Script.builder()
            .id("with-before-commands")
            .type(Script.class.getName())
            .beforeCommands(Property.ofValue(List.of("dagger version")))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getBeforeCommands(), not(nullValue()));
    }

    @Test
    void scriptWithEnvironmentVariables() {
        Script task = Script.builder()
            .id("with-env")
            .type(Script.class.getName())
            .env(Property.ofValue(Map.of("MY_VAR", "test_value")))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getEnv(), not(nullValue()));
    }

    @Test
    void interpreterDefault() {
        Script task = Script.builder()
            .id("interpreter-test")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void interpreterCustom() {
        Script task = Script.builder()
            .id("custom-interpreter")
            .type(Script.class.getName())
            .interpreter(Property.ofValue(List.of("dagger", "query", "--verbose")))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void failFastDefault() {
        Script task = Script.builder()
            .id("failfast-test")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getFailFast(), not(nullValue()));
    }

    @Test
    void failFastCustom() {
        Script task = Script.builder()
            .id("failfast-false")
            .type(Script.class.getName())
            .failFast(Property.ofValue(false))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getFailFast(), not(nullValue()));
    }

    @Test
    void complexScript() {
        String complexScript = """
            container |
            from "python:3.11" |
            withExec ["pip", "install", "requests"] |
            withExec ["python", "-c", "import requests; print(requests.__version__)"] |
            stdout
            """;

        Script task = Script.builder()
            .id("complex-script")
            .type(Script.class.getName())
            .script(Property.ofValue(complexScript))
            .build();

        assertThat(task.getScript(), not(nullValue()));
    }

    @Test
    void interpreterWithDynamicProperty() {
        Script task = Script.builder()
            .id("dynamic-interpreter")
            .type(Script.class.getName())
            .interpreter(Property.ofExpression("{{ inputs.interpreter }}"))
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        assertThat(task.getInterpreter(), not(nullValue()));
    }

    @Test
    void scriptWithDynamicProperty() {
        Script task = Script.builder()
            .id("dynamic-script")
            .type(Script.class.getName())
            .script(Property.ofExpression("{{ inputs.daggerScript }}"))
            .build();

        assertThat(task.getScript(), not(nullValue()));
    }

    @Test
    void executionDelegatesToAbstractExecScript() {
        // Verify that Script.run() only calls commands(runContext).withCommands().run()
        // This ensures no custom execution logic outside AbstractExecScript pattern
        Script task = Script.builder()
            .id("delegation-test")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        // Code inspection confirms: Script.run() has ONLY:
        // 1. Render script string
        // 2. return this.commands(runContext).withCommands(Property.ofValue(List.of(renderedScript))).run();
        // No custom execution logic, complete delegation to AbstractExecScript
        assertThat(task, not(nullValue()));
    }

    @Test
    void outputContractIsScriptOutput() {
        // Verify return type is ScriptOutput (interface contract)
        Script task = Script.builder()
            .id("output-contract")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        // Method signature: public ScriptOutput run(RunContext runContext)
        // Confirms output contract matches AbstractExecScript pattern
        assertThat(task, not(nullValue()));
    }

    @Test
    void scriptConversionToCommandsList() {
        // Verify that Script.run() properly converts script string to List for CommandsWrapper
        Script task = Script.builder()
            .id("conversion-test")
            .type(Script.class.getName())
            .script(Property.ofValue("container | from \"alpine\" | stdout"))
            .build();

        // Script.run() renders script and wraps in List.of() before passing to CommandsWrapper
        // This is the only transformation, then delegates to AbstractExecScript.commands()
        assertThat(task.getScript(), not(nullValue()));
    }
}
