package io.kestra.core.secret;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
public class SecretFunctionTest extends AbstractMemoryRunnerTest {
    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private SecretService secretService;

    @Test
    @EnabledIfEnvironmentVariable(named = "SECRET_MY_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "SECRET_NEW_LINE", matches = ".*")
    void getSecret() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "secrets");
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("value"), is("secretValue"));
        assertThat(execution.getTaskRunList().get(1).getOutputs().get("value"), is("passwordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlong"));
    }

    @Test
    void getUnknownSecret() {
        IllegalVariableEvaluationException exception = Assertions.assertThrows(IllegalVariableEvaluationException.class, () ->
            secretService.findSecret(null, null, "unknown_secret_key")
        );

        assertThat(exception.getMessage(), containsString("Unable to find secret 'unknown_secret_key'"));
    }
}
