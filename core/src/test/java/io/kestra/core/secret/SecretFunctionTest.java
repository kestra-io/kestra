package io.kestra.core.secret;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class SecretFunctionTest extends AbstractMemoryRunnerTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> logQueue;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private SecretService secretService;

    @Test
    @EnabledIfEnvironmentVariable(named = "SECRET_MY_SECRET", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "SECRET_NEW_LINE", matches = ".*")
    void getSecret() throws TimeoutException {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, either -> logs.add(either.getLeft()));

        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "secrets");
        assertThat(execution.getTaskRunList().get(0).getOutputs().get("value"), is("secretValue"));
        assertThat(execution.getTaskRunList().get(2).getOutputs().get("value"), is("passwordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlongpasswordveryveryveyrlong"));

        LogEntry matchingLog = TestsUtils.awaitLog(logs, logEntry -> logEntry.getTaskId() != null && logEntry.getTaskId().equals("log-secret"));
        receive.blockLast();
        assertThat(matchingLog.getMessage(), containsString("***"));
    }

    @Test
    void getUnknownSecret() throws IllegalVariableEvaluationException, IOException {
        String secret = secretService.findSecret(null, null, "unknown_secret_key");

        assertThat(secret, nullValue());
    }
}
