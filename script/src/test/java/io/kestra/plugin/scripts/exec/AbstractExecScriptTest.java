package io.kestra.plugin.scripts.exec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.core.runner.Process;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.exec.scripts.runners.CommandsWrapper;

import jakarta.inject.Inject;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class AbstractExecScriptTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldIgnoreNullEnvironmentValueWhenRunningCommands() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("KESTRA_TEST_NULL_ENV", null);
        env.put("KESTRA_TEST_OK_ENV", "ok");

        EnvScript script = EnvScript.builder()
            .id(IdUtils.create())
            .type(EnvScript.class.getName())
            .taskRunner(Process.instance())
            .env(Property.ofValue(env))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, Map.of());

        CommandsWrapper wrapper = script.wrapper(runContext);

        assertThat(wrapper.getEnv())
            .doesNotContainKey("KESTRA_TEST_NULL_ENV")
            .containsEntry("KESTRA_TEST_OK_ENV", "ok");

        ScriptOutput output = wrapper
            .withInterpreter(Property.ofValue(List.of("/bin/sh", "-c")))
            .withCommands(Property.ofValue(List.of("test \"${KESTRA_TEST_NULL_ENV+x}\" = \"\" && test \"$KESTRA_TEST_OK_ENV\" = ok")))
            .run();

        assertThat(output.getExitCode()).isZero();
    }

    @SuperBuilder
    @NoArgsConstructor
    @Getter
    private static class EnvScript extends AbstractExecScript {
        private Property<String> containerImage;

        CommandsWrapper wrapper(RunContext runContext) throws Exception {
            return commands(runContext);
        }
    }
}
