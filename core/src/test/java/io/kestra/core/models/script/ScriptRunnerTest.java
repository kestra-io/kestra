package io.kestra.core.models.script;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ScriptRunnerTest {
    public static final String ADDITIONAL_VAR_KEY = "additionalVarKey";
    public static final String ADDITIONAL_ENV_KEY = "ADDITIONAL_ENV_KEY";

    @Test
    void additionalVarsAndEnv() throws IllegalVariableEvaluationException {
        ScriptRunner scriptRunner = new ScriptRunnerAdditional(true);
        ScriptCommands scriptCommands = new ScriptCommandsAdditional();

        RunContext runContext = new RunContext();
        assertThat(scriptRunner.additionalVars(runContext, scriptCommands), is(Map.of(
            ScriptService.VAR_BUCKET_PATH, ScriptRunnerAdditional.RUNNER_BUCKET_PATH,
            ScriptService.VAR_WORKING_DIR, ScriptRunnerAdditional.RUNNER_WORKING_DIR,
            ScriptService.VAR_OUTPUT_DIR, ScriptRunnerAdditional.RUNNER_OUTPUT_DIR,
            ScriptRunnerAdditional.RUNNER_ADDITIONAL_VAR_KEY, ScriptRunnerAdditional.RUNNER_ADDITIONAL_VAR_VALUE,
            ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_VAR_KEY, ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_VAR_VALUE,
            ADDITIONAL_VAR_KEY, ScriptRunnerAdditional.ADDITIONAL_VAR_VALUE
        )));

        assertThat(scriptRunner.env(runContext, scriptCommands), is(Map.of(
            ScriptService.ENV_BUCKET_PATH, ScriptRunnerAdditional.OVERRIDEN_ENV_BUCKET_PATH,
            ScriptService.ENV_WORKING_DIR, ScriptRunnerAdditional.OVERRIDEN_ENV_WORKING_DIR,
            ScriptService.ENV_OUTPUT_DIR, ScriptRunnerAdditional.OVERRIDEN_ENV_OUTPUT_DIR,
            ScriptRunnerAdditional.RUNNER_ADDITIONAL_ENV_KEY, ScriptRunnerAdditional.RUNNER_ADDITIONAL_ENV_VALUE,
            ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY, ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_ENV_VALUE,
            ADDITIONAL_ENV_KEY, ScriptRunnerAdditional.ADDITIONAL_ENV_VALUE
        )));

        scriptRunner = new ScriptRunnerAdditional(false);
        assertThat(scriptRunner.env(runContext, scriptCommands), is(Map.of(
            ScriptService.ENV_BUCKET_PATH, ScriptRunnerAdditional.RUNNER_BUCKET_PATH,
            ScriptService.ENV_WORKING_DIR, ScriptRunnerAdditional.RUNNER_WORKING_DIR,
            ScriptService.ENV_OUTPUT_DIR, ScriptRunnerAdditional.RUNNER_OUTPUT_DIR,
            ScriptRunnerAdditional.RUNNER_ADDITIONAL_ENV_KEY, ScriptRunnerAdditional.RUNNER_ADDITIONAL_ENV_VALUE,
            ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY, ScriptCommandsAdditional.SCRIPT_COMMANDS_ADDITIONAL_ENV_VALUE,
            ADDITIONAL_ENV_KEY, ScriptRunnerAdditional.ADDITIONAL_ENV_VALUE
        )));
    }

    private static class ScriptRunnerAdditional extends ScriptRunner {

        public static final String RUNNER_BUCKET_PATH = "runnerBucketPath";
        public static final String RUNNER_WORKING_DIR = "runnerWorkingDir";
        public static final String RUNNER_OUTPUT_DIR = "runnerOutputDir";
        public static final String RUNNER_ADDITIONAL_VAR_KEY = "runnerAdditionalKey";
        public static final String RUNNER_ADDITIONAL_VAR_VALUE = "runnerAdditionalValue";
        public static final String RUNNER_ADDITIONAL_ENV_KEY = "RUNNER_ADDITIONAL_ENV_KEY";
        public static final String RUNNER_ADDITIONAL_ENV_VALUE = "runnerAdditionalEnvValue";
        public static final String ADDITIONAL_VAR_VALUE = "runnerSpecificVarValue";
        public static final String ADDITIONAL_ENV_VALUE = "runnerSpecificEnvValue";
        private static final String OVERRIDEN_ENV_WORKING_DIR = "overridenEnvWorkingDir";
        private static final String OVERRIDEN_ENV_OUTPUT_DIR = "overridenEnvOutputDir";
        private static final String OVERRIDEN_ENV_BUCKET_PATH = "overridenEnvBucketPath";

        private final boolean overrideEnvValues;

        public ScriptRunnerAdditional(boolean overrideEnvValues) {
            this.overrideEnvValues = overrideEnvValues;
        }

        @Override
        public RunnerResult run(RunContext runContext, ScriptCommands scriptCommands, List<String> filesToUpload, List<String> filesToDownload) {
            return null;
        }

        @Override
        protected Map<String, Object> runnerAdditionalVars(RunContext runContext, ScriptCommands scriptCommands) {
            return Map.of(
                ScriptService.VAR_BUCKET_PATH, RUNNER_BUCKET_PATH,
                ScriptService.VAR_WORKING_DIR, RUNNER_WORKING_DIR,
                ScriptService.VAR_OUTPUT_DIR, RUNNER_OUTPUT_DIR,
                RUNNER_ADDITIONAL_VAR_KEY, RUNNER_ADDITIONAL_VAR_VALUE,
                ADDITIONAL_VAR_KEY, ADDITIONAL_VAR_VALUE
            );
        }

        @Override
        protected Map<String, String> runnerEnv(RunContext runContext, ScriptCommands scriptCommands) {
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

    private static class ScriptCommandsAdditional implements ScriptCommands {

        public static final String SCRIPT_COMMANDS_ADDITIONAL_VAR_KEY = "scriptCommandsAdditionalVarKey";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_VAR_VALUE = "scriptCommandsAdditionalVarValue";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY = "SCRIPT_COMMANDS_ADDITIONAL_ENV_KEY";
        public static final String SCRIPT_COMMANDS_ADDITIONAL_ENV_VALUE = "scriptCommandsAdditionalEnvValue";
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
    }
}
