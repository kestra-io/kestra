package io.kestra.jdbc.runner;

import io.kestra.core.queues.QueueException;
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
    void retrySuccess() throws TimeoutException, QueueException {
        retryCaseTest.retrySuccess();
    }

    @Test
    void retrySuccessAtFirstAttempt() throws TimeoutException, QueueException {
        retryCaseTest.retrySuccessAtFirstAttempt();
    }

    @Test
    void retryFailed() throws TimeoutException, QueueException {
        retryCaseTest.retryFailed();
    }

    @Test
    void retryRandom() throws TimeoutException, QueueException {
        retryCaseTest.retryRandom();
    }

    @Test
    void retryExpo() throws TimeoutException, QueueException {
        retryCaseTest.retryExpo();
    }

    @Test
    void retryFail() throws TimeoutException, QueueException {
        retryCaseTest.retryFail();
    }

    @Test
    void retryNewExecutionTaskDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionTaskDuration();
    }

    @Test
    void retryNewExecutionTaskAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionTaskAttempts();
    }

    @Test
    void retryNewExecutionFlowDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionFlowDuration();
    }

    @Test
    void retryNewExecutionFlowAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionFlowAttempts();
    }

    @Test
    void retryFailedTaskDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryFailedTaskDuration();
    }

    @Test
    void retryFailedTaskAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryFailedTaskAttempts();
    }

    @Test
    void retryFailedFlowDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryFailedFlowDuration();
    }

    @Test
    void retryFailedFlowAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryFailedFlowAttempts();
    }

    @Test
    void retryFlowable() throws TimeoutException, QueueException {
        retryCaseTest.retryFlowable();
    }

    @Test
    void retrySubflow() throws TimeoutException, QueueException {
        retryCaseTest.retrySubflow();
    }

    @Test
    void retryFlowableChild() throws TimeoutException, QueueException {
        retryCaseTest.retryFlowableChild();
    }

    @Test
    void retryFlowableNestedChild() throws TimeoutException, QueueException {
        retryCaseTest.retryFlowableNestedChild();
    }

    @Test
    void retryFlowableParallel() throws TimeoutException, QueueException {
        retryCaseTest.retryFlowableParallel();
    }

    @Test
    void retryDynamicTask() throws TimeoutException, QueueException {
        retryCaseTest.retryDynamicTask();
    }
}
