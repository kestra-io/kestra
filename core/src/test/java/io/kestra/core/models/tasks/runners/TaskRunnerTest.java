package io.kestra.core.models.tasks.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.micronaut.context.ApplicationContext;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class TaskRunnerTest {
    public static final String ADDITIONAL_VAR_KEY = "additionalVarKey";
    public static final String ADDITIONAL_ENV_KEY = "ADDITIONAL_ENV_KEY";

    @Inject
    ApplicationContext applicationContext;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void additionalVarsAndEnv() throws IllegalVariableEvaluationException {
        TaskRunner<?> taskRunner = new TaskRunnerAdditional(true);
        TaskCommands taskCommands = new TaskCommandsAdditional();

        Map<String, Object> contextVariables = Map.of(
            "runnerBucketPath", "someRunnerBucketPath",
            "runnerAdditionalVarKey", "runnerVarKey",
            "runnerAdditionalVarValue", "runnerVarValue",
            "runnerAdditionalEnvKey", "runnerEnvKey",
            "runnerAdditionalEnvValue", "runnerEnvValue",
            "scriptCommandsAdditionalVarKey", "scriptCommandsVarKey",
            "scriptCommandsAdditionalVarValue", "scriptCommandsVarValue",
            "scriptCommandsAdditionalEnvKey", "scriptCommandsEnvKey",
            "scriptCommandsAdditionalEnvValue", "scriptCommandsEnvValue"
        );
        RunContext runContext = runContextFactory.of(contextVariables);

        assertThat(taskRunner.additionalVars(runContext, taskCommands), is(Map.of(
            ScriptService.VAR_BUCKET_PATH, contextVariables.get("runnerBucketPath"),
            ScriptService.VAR_WORKING_DIR, TaskRunnerAdditional.RUNNER_WORKING_DIR,
            ScriptService.VAR_OUTPUT_DIR, TaskRunnerAdditional.RUNNER_OUTPUT_DIR,
            contextVariables.get("runnerAdditionalVarKey"), contextVariables.get("runnerAdditionalVarValue"),
            contextVariables.get("scriptCommandsAdditionalVarKey"), contextVariables.get("scriptCommandsAdditionalVarValue"),
            ADDITIONAL_VAR_KEY, TaskRunnerAdditional.ADDITIONAL_VAR_VALUE
        )));

        assertThat(taskRunner.env(runContext, taskCommands), is(Map.of(
            ScriptService.ENV_BUCKET_PATH, TaskRunnerAdditional.OVERRIDEN_ENV_BUCKET_PATH,
            ScriptService.ENV_WORKING_DIR, TaskRunnerAdditional.OVERRIDEN_ENV_WORKING_DIR,
            ScriptService.ENV_OUTPUT_DIR, TaskRunnerAdditional.OVERRIDEN_ENV_OUTPUT_DIR,
            contextVariables.get("runnerAdditionalEnvKey"), contextVariables.get("runnerAdditionalEnvValue"),
            contextVariables.get("scriptCommandsAdditionalEnvKey"), contextVariables.get("scriptCommandsAdditionalEnvValue"),
            ADDITIONAL_ENV_KEY, TaskRunnerAdditional.ADDITIONAL_ENV_VALUE
        )));

        taskRunner = new TaskRunnerAdditional(false);
        assertThat(taskRunner.env(runContext, taskCommands), is(Map.of(
            ScriptService.ENV_BUCKET_PATH, contextVariables.get("runnerBucketPath"),
            ScriptService.ENV_WORKING_DIR, TaskRunnerAdditional.RUNNER_WORKING_DIR,
            ScriptService.ENV_OUTPUT_DIR, TaskRunnerAdditional.RUNNER_OUTPUT_DIR,
            contextVariables.get("runnerAdditionalEnvKey"), contextVariables.get("runnerAdditionalEnvValue"),
            contextVariables.get("scriptCommandsAdditionalEnvKey"), contextVariables.get("scriptCommandsAdditionalEnvValue"),
            ADDITIONAL_ENV_KEY, TaskRunnerAdditional.ADDITIONAL_ENV_VALUE
        )));
    }

    private static class TaskRunnerAdditional extends TaskRunner<TaskRunnerDetailResult> {

        public static final String RUNNER_BUCKET_PATH = "{{runnerBucketPath}}";
        public static final String RUNNER_WORKING_DIR = "runnerWorkingDir";
        public static final String RUNNER_OUTPUT_DIR = "runnerOutputDir";
        public static final String RUNNER_ADDITIONAL_VAR_KEY = "{{runnerAdditionalVarKey}}";
        public static final String RUNNER_ADDITIONAL_VAR_VALUE = "{{runnerAdditionalVarValue}}";
        public static final String RUNNER_ADDITIONAL_ENV_KEY = "{{runnerAdditionalEnvKey}}";
        public static final String RUNNER_ADDITIONAL_ENV_VALUE = "{{runnerAdditionalEnvValue}}";
        public static final String ADDITIONAL_VAR_VALUE = "runnerSpecificVarValue";
        public static final String ADDITIONAL_ENV_VALUE = "runnerSpecificEnvValue";
        private static final String OVERRIDEN_ENV_WORKING_DIR = "overridenEnvWorkingDir";
        private static final String OVERRIDEN_ENV_OUTPUT_DIR = "overridenEnvOutputDir";
        private static final String OVERRIDEN_ENV_BUCKET_PATH = "overridenEnvBucketPath";

        private final boolean overrideEnvValues;

        public TaskRunnerAdditional(boolean overrideEnvValues) {
            this.overrideEnvValues = overrideEnvValues;
        }

        @Override
        public TaskRunnerResult<TaskRunnerDetailResult> run(RunContext runContext, TaskCommands taskCommands, List<String> filesToDownload) {
            return null;
        }

        @Override
        protected Map<String, Object> runnerAdditionalVars(RunContext runContext, TaskCommands taskCommands) {
            return Map.of(
                ScriptService.VAR_BUCKET_PATH, RUNNER_BUCKET_PATH,
                ScriptService.VAR_WORKING_DIR, RUNNER_WORKING_DIR,
                ScriptService.VAR_OUTPUT_DIR, RUNNER_OUTPUT_DIR,
                RUNNER_ADDITIONAL_VAR_KEY, RUNNER_ADDITIONAL_VAR_VALUE,
                ADDITIONAL_VAR_KEY, ADDITIONAL_VAR_VALUE
            );
        }

        @Override
        protected Map<String, String> runnerEnv(RunContext runContext, TaskCommands taskCommands) {
            Map<String, String> env = new HashMap<>(Map.of(
                RUNNER_ADDITIONAL_ENV_KEY, RUNNER_ADDITIONAL_ENV_VALUE,
                ADDITIONAL_ENV_KEY, ADDITIONAL_ENV_VALUE
            ));

            if (overrideEnvValues) {
                env.put(ScriptService.ENV_WORKING_DIR, OVERRIDEN_ENV_WORKING_DIR);
                env.put(ScriptService.ENV_OUTPUT_DIR, OVERRIDEN_ENV_OUTPUT_DIR);
                env.put(ScriptService.ENV_BUCKET_PATH, OVERRIDEN_ENV_BUCKET_PATH);
            }

            return env;
        }
    }

    private static class TaskCommandsAdditional implements TaskCommands {

        public static final String SCRIPT_COMMANDS_ADDITIONAL_VAR_KEY = "{{scriptCommandsAdditionalVarKey}}";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_VAR_VALUE = "{{scriptCommandsAdditionalVarValue}}";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY = "{{scriptCommandsAdditionalEnvKey}}";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_ENV_VALUE = "{{scriptCommandsAdditionalEnvValue}}";
        public static final String ADDITIONAL_VAR_VALUE = "commandsSpecificVarValue";
        public static final String ADDITIONAL_ENV_VALUE = "commandsSpecificEnvValue";

        @Override
        public String getContainerImage() {
            return null;
        }

        @Override
        public AbstractLogConsumer getLogConsumer() {
            return null;
        }

        @Override
        public List<String> getCommands() {
            return null;
        }

        @Override
        public TargetOS getTargetOS() {
            return null;
        }

        @Override
        public Map<String, Object> getAdditionalVars() {
            return Map.of(
                SCRIPT_COMMANDS_ADDITIONAL_VAR_KEY, SCRIPT_COMMANDS_ADDITIONAL_VAR_VALUE,
                ADDITIONAL_VAR_KEY, ADDITIONAL_VAR_VALUE
            );
        }

        @Override
        public Path getWorkingDirectory() {
            return null;
        }

        @Override
        public Path getOutputDirectory() {
            return null;
        }

        @Override
        public Map<String, String> getEnv() {
            return Map.of(
                SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY, SCRIPT_COMMANDS_ADDITIONAL_ENV_VALUE,
                ADDITIONAL_ENV_KEY, ADDITIONAL_ENV_VALUE
            );
        }

        @Override
        public Boolean getEnableOutputDirectory() {
            return true;
        }

        @Override
        public Duration getTimeout() {
            return null;
        }
    }
}
