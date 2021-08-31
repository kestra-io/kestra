package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.tasks.debugs.Return;
import org.junit.jupiter.api.Test;

import java.util.List;
import javax.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;

class ExecutionServiceTest extends AbstractMemoryRunnerTest {
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Test
    void simple() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), is(execution.getId()));
        assertThat(restart.getTaskRunList().get(2).getId(), is(execution.getTaskRunList().get(2).getId()));
    }

    @Test
    void simpleRevision() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));


        Flow flow = flowRepository.findById("io.kestra.tests", "restart_last_failed").orElseThrow();
        flowRepository.update(flow, flow.updateTask(
            "a",
            Return.builder()
                .id("a")
                .type(Return.class.getName())
                .format("replace")
                .build()
        ));


        Execution restart = executionService.restart(execution, null, 2);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(2).getId(), not(execution.getTaskRunList().get(2).getId()));
    }

    @Test
    void flowable() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(7));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), is(3L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), is(4L));
        assertThat(restart.getTaskRunList().get(0).getId(), is(restart.getTaskRunList().get(0).getId()));
    }

    @Test
    void flowable2() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));
        assertThat(execution.getTaskRunList(), hasSize(16));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(16));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), is(6L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), is(7L));
        assertThat(restart.getTaskRunList().get(0).getId(), is(restart.getTaskRunList().get(0).getId()));
    }

    @Test
    void flowableRestartTask() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "NO"));
        assertThat(execution.getTaskRunList(), hasSize(20));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.restart(execution, "2_end", null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(20));
        assertThat(restart.getTaskRunList().get(19).getState().getCurrent(), is(State.Type.RESTARTED));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }
}