package io.kestra.jdbc.runner;

import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.plugin.core.flow.RetryCaseTest;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeoutException;

@KestraTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcRunnerRetryTest {
    @Inject
    private StandAloneRunner runner;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private RetryCaseTest retryCaseTest;

    @BeforeAll
    void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(repositoryLoader);
        runner.setSchedulerEnabled(false);
        runner.run();
    }

    @Test
    void retrySuccess() throws TimeoutException {
        retryCaseTest.retrySuccess();
    }

    @Test
    void retrySuccessAtFirstAttempt() throws TimeoutException {
        retryCaseTest.retrySuccessAtFirstAttempt();
    }

    @Test
    void retryFailed() throws TimeoutException {
        retryCaseTest.retryFailed();
    }

    @Test
    void retryRandom() throws TimeoutException {
        retryCaseTest.retryRandom();
    }

    @Test
    void retryExpo() throws TimeoutException {
        retryCaseTest.retryExpo();
    }

    @Test
    void retryFail() throws TimeoutException {
        retryCaseTest.retryFail();
    }

    @Test
    void retryNewExecutionTaskDuration() throws TimeoutException {
        retryCaseTest.retryNewExecutionTaskDuration();
    }

    @Test
    void retryNewExecutionTaskAttempts() throws TimeoutException {
        retryCaseTest.retryNewExecutionTaskAttempts();
    }

    @Test
    void retryNewExecutionFlowDuration() throws TimeoutException {
        retryCaseTest.retryNewExecutionFlowDuration();
    }

    @Test
    void retryNewExecutionFlowAttempts() throws TimeoutException {
        retryCaseTest.retryNewExecutionFlowAttempts();
    }

    @Test
    void retryFailedTaskDuration() throws TimeoutException {
        retryCaseTest.retryFailedTaskDuration();
    }

    @Test
    void retryFailedTaskAttempts() throws TimeoutException {
        retryCaseTest.retryFailedTaskAttempts();
    }

    @Test
    void retryFailedFlowDuration() throws TimeoutException {
        retryCaseTest.retryFailedFlowDuration();
    }

    @Test
    void retryFailedFlowAttempts() throws TimeoutException {
        retryCaseTest.retryFailedFlowAttempts();
    }

    @Test
    void retryFlowable() throws TimeoutException {
        retryCaseTest.retryFlowable();
    }

    @Test
    void retryFlowableChild() throws TimeoutException {
        retryCaseTest.retryFlowableChild();
    }

    @Test
    void retryFlowableNestedChild() throws TimeoutException {
        retryCaseTest.retryFlowableNestedChild();
    }

    @Test
    void retryFlowableParallel() throws TimeoutException {
        retryCaseTest.retryFlowableParallel();
    }

    @Test
    void retryDynamicTask() throws TimeoutException {
        retryCaseTest.retryDynamicTask();
    }
}
