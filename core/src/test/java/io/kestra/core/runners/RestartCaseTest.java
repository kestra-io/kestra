package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;

import java.time.Duration;
import javax.inject.Inject;
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

    public void restartTask() throws Exception {
        Flow flow = flowRepository.findById("io.kestra.tests", "restart_with_sequential").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(flow.getNamespace(), flow.getId(), Duration.ofSeconds(60));

        assertThat(firstExecution.getState().getCurrent(), is(State.Type.SUCCESS));

        // wait
        Execution finishedRestartedExecution = runnerUtils.awaitChildExecution(
            flow,
            firstExecution,
            throwRunnable(() -> {
                Thread.sleep(1000);
                Execution restartedExec = executionService.restart(firstExecution, "a-3-2-2_end");

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getParentId(), is(firstExecution.getId()));
                assertThat(restartedExec.getTaskRunList().size(), is(8));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RESTARTED));

                assertThat(restartedExec.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(restartedExec.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(restartedExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(restartedExec.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(restartedExec.getTaskRunList().get(5).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(restartedExec.getTaskRunList().get(6).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(restartedExec.getTaskRunList().get(7).getState().getCurrent(), is(State.Type.RESTARTED));
                assertThat(restartedExec.getTaskRunList().get(7).getAttempts().size(), is(1));
            }),
            Duration.ofSeconds(60)
        );

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(not(firstExecution.getId())));
        assertThat(finishedRestartedExecution.getParentId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
