package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.debugs.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionServiceTest extends AbstractMemoryRunnerTest {
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    TaskDefaultService taskDefaultService;

    @Test
    void restartSimple() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), is(execution.getId()));
        assertThat(restart.getTaskRunList().get(2).getId(), is(execution.getTaskRunList().get(2).getId()));
    }

    @Test
    void restartSimpleRevision() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Flow flow = flowRepository.findById("io.kestra.tests", "restart_last_failed").orElseThrow();
        flowRepository.update(
            flow,
            flow.updateTask(
                "a",
                Return.builder()
                    .id("a")
                    .type(Return.class.getName())
                    .format("replace")
                    .build()
            ),
            JacksonMapper.ofYaml().writeValueAsString(flow),
            taskDefaultService.injectDefaults(flow)
        );


        Execution restart = executionService.restart(execution, 2);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(2).getId(), not(execution.getTaskRunList().get(2).getId()));
    }

    @RetryingTest(5)
    void restartFlowable() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().get(0).getId(), is(restart.getTaskRunList().get(0).getId()));
    }

    @RetryingTest(5)
    void restartFlowable2() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().get(0).getId(), is(restart.getTaskRunList().get(0).getId()));
    }

    @Test
    void restartDynamic() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "working-directory", null, (f, e) -> ImmutableMap.of("failed", "true"));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);
        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));

        assertThat(restart.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(0).getState().getHistories(), hasSize(4));
    }

    @Test
    void replayFromBeginning() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, null, null);

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getNamespace(), is("io.kestra.tests"));
        assertThat(restart.getFlowId(), is("logs"));

        assertThat(restart.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(restart.getState().getHistories(), hasSize(1));
        assertThat(restart.getState().getHistories().get(0).getDate(), not(is(execution.getState().getStartDate())));
        assertThat(restart.getTaskRunList(), hasSize(0));

        assertThat(restart.getId(), not(execution.getId()));
    }

    @Test
    void replaySimple() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.getTaskRunList().get(1).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(2));
        assertThat(restart.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(1).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void replayFlowable() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "NO"));
        assertThat(execution.getTaskRunList(), hasSize(20));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(20));
        assertThat(restart.getTaskRunList().get(19).getState().getCurrent(), is(State.Type.RESTARTED));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void replayParallel() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "parallel-nested");
        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(8));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void replayEachSeq() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-sequential-nested");
        assertThat(execution.getTaskRunList(), hasSize(23));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(5));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void replayEachSeq2() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-sequential-nested");
        assertThat(execution.getTaskRunList(), hasSize(23));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-2-1_return", List.of("s1", "a a")).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(6));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void replayEachPara() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel-nested");
        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(8));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
    }

    @Test
    void markAsEachPara() throws Exception {
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-parallel-nested");
        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.markAs(execution, execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(11));
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories(), hasSize(4));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getAttempts(), nullValue());

        restart = executionService.markAs(execution, execution.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(11));
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getHistories(), hasSize(4));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getAttempts().get(0).getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void resumePausedToRunning() throws TimeoutException, InternalException {
        Execution execution = runnerUtils.runOneUntilPaused("io.kestra.tests", "pause");
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

        Execution resume = executionService.resume(execution, State.Type.RUNNING);

        assertThat(resume.getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(resume.getState().getHistories(), hasSize(4));

        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> executionService.resume(resume, State.Type.RUNNING)
        );
    }

    @Test
    void resumePausedToKilling() throws TimeoutException, InternalException {
        Execution execution = runnerUtils.runOneUntilPaused("io.kestra.tests", "pause");
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

        Execution resume = executionService.resume(execution, State.Type.KILLING);

        assertThat(resume.getState().getCurrent(), is(State.Type.KILLING));
        assertThat(resume.getState().getHistories(), hasSize(4));
    }
}