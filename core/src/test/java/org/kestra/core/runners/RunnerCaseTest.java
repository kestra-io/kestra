package org.kestra.core.runners;

import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.services.ExecutionService;

import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.kestra.core.utils.Rethrow.throwRunnable;

@Singleton
public class RunnerCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private RunnerUtils runnerUtils;

    @Inject
    private ExecutionService executionService;

    public void restart() throws Exception {
        Flow flow = flowRepository.findById("org.kestra.tests", "restart_last_failed").orElseThrow();

        Execution firstExecution = runnerUtils.runOne(flow.getNamespace(), flow.getId());

        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getTaskRunList(), hasSize(3));
        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));

        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            flow,
            firstExecution,
            throwRunnable(() -> {
                Thread.sleep(100);
                Execution restartedExec = executionService.restart(firstExecution, null);

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(3));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RUNNING));

            }),
            Duration.ofSeconds(15)
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
}
