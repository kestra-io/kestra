package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;

import java.time.Duration;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static io.kestra.core.utils.Rethrow.throwRunnable;

@Singleton
public class RestartCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionService executionService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    public void restartFailed() throws Exception {
        Flow flow = flowRepository.findById("io.kestra.tests", "restart_last_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getTaskRunList(), hasSize(3));
        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getState().getCurrent() == State.Type.SUCCESS,
            throwRunnable(() -> {
                Thread.sleep(1000);
                Execution restartedExec = executionService.restart(firstExecution, null);
                executionQueue.emit(restartedExec);

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(3));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RESTARTED));
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getParentId(), nullValue());
        assertThat(finishedRestartedExecution.getTaskRunList().size(), is(4));

        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts().size(), is(2));

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent(), is(State.Type.SUCCESS)));
    }

    public void replay() throws Exception {
        Flow flow = flowRepository.findById("io.kestra.tests", "restart-each").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent(), is(State.Type.SUCCESS));

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitChildExecution(
            flow,
            firstExecution,
            throwRunnable(() -> {
                Thread.sleep(1000);
                Execution restartedExec = executionService.replay(firstExecution, firstExecution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null);
                executionQueue.emit(restartedExec);

                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RESTARTED));
                assertThat(restartedExec.getState().getHistories(), hasSize(4));
                assertThat(restartedExec.getTaskRunList(), hasSize(20));
                assertThat(restartedExec.getTaskRunList().get(19).getState().getCurrent(), is(State.Type.RESTARTED));

                assertThat(restartedExec.getId(), not(firstExecution.getId()));
                assertThat(restartedExec.getTaskRunList().get(1).getId(), not(firstExecution.getTaskRunList().get(1).getId()));
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(not(firstExecution.getId())));
        assertThat(finishedRestartedExecution.getParentId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
