package io.kestra.jdbc.runner;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.queues.QueueException;
import io.kestra.plugin.core.flow.RetryCaseTest;
import jakarta.inject.Inject;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // must be per-class to allow calling once init() which took a lot of time
public abstract class JdbcRunnerRetryTest {

    @Inject
    private RetryCaseTest retryCaseTest;

    @Test
    @ExecuteFlow("flows/valids/retry-success.yaml")
    void retrySuccess(Execution execution){
        retryCaseTest.retrySuccess(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-success-first-attempt.yaml")
    void retrySuccessAtFirstAttempt(Execution execution){
        retryCaseTest.retrySuccessAtFirstAttempt(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-failed.yaml")
    void retryFailed(Execution execution){
        retryCaseTest.retryFailed(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-random.yaml")
    void retryRandom(Execution execution){
        retryCaseTest.retryRandom(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-expo.yaml")
    void retryExpo(Execution execution){
        retryCaseTest.retryExpo(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-fail.yaml")
    void retryFail(Execution execution){
        retryCaseTest.retryFail(execution);
    }

    @Test
    @LoadFlows({"flows/valids/retry-new-execution-task-duration.yml"})
    void retryNewExecutionTaskDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionTaskDuration();
    }

    @Test
    @LoadFlows({"flows/valids/retry-new-execution-task-attempts.yml"})
    void retryNewExecutionTaskAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionTaskAttempts();
    }

    @Test
    @LoadFlows({"flows/valids/retry-new-execution-flow-duration.yml"})
    void retryNewExecutionFlowDuration() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionFlowDuration();
    }

    @Test
    @LoadFlows({"flows/valids/retry-new-execution-flow-attempts.yml"})
    void retryNewExecutionFlowAttempts() throws TimeoutException, QueueException {
        retryCaseTest.retryNewExecutionFlowAttempts();
    }

    @Test
    @ExecuteFlow("flows/valids/retry-failed-task-duration.yml")
    void retryFailedTaskDuration(Execution execution){
        retryCaseTest.retryFailedTaskDuration(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-failed-task-attempts.yml")
    void retryFailedTaskAttempts(Execution execution){
        retryCaseTest.retryFailedTaskAttempts(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-failed-flow-duration.yml")
    void retryFailedFlowDuration(Execution execution){
        retryCaseTest.retryFailedFlowDuration(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-failed-flow-attempts.yml")
    void retryFailedFlowAttempts(Execution execution){
        retryCaseTest.retryFailedFlowAttempts(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-flowable.yaml")
    void retryFlowable(Execution execution){
        retryCaseTest.retryFlowable(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-subflow.yaml")
    void retrySubflow(Execution execution){
        retryCaseTest.retrySubflow(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-flowable-child.yaml")
    void retryFlowableChild(Execution execution){
        retryCaseTest.retryFlowableChild(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-flowable-nested-child.yaml")
    void retryFlowableNestedChild(Execution execution){
        retryCaseTest.retryFlowableNestedChild(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-flowable-parallel.yaml")
    void retryFlowableParallel(Execution execution){
        retryCaseTest.retryFlowableParallel(execution);
    }

    @Test
    @ExecuteFlow("flows/valids/retry-dynamic-task.yaml")
    void retryDynamicTask(Execution execution){
        retryCaseTest.retryDynamicTask(execution);
    }
}
