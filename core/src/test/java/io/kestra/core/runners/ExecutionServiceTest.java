package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.debugs.Return;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.hamcrest.core.Every;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ExecutionServiceTest extends AbstractMemoryRunnerTest {
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    TaskDefaultService taskDefaultService;

    @Inject
    ExecutionRepositoryInterface executionRepository;

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

    @Test
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

    @Test
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
        Execution execution = runnerUtils.runOne("io.kestra.tests", "worker", null, (f, e) -> ImmutableMap.of("failed", "true"));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);
        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));

        assertThat(restart.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(0).getState().getHistories(), hasSize(4));
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

        ExecutionService.UpdateStatusInputDto updateToFailStatus = ExecutionService.UpdateStatusInputDto.builder()
            .execution(execution)
            .taskRunIds(List.of(execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId()))
            .newState(State.Type.FAILED)
            .build();
        Execution restart = executionService.markAs(updateToFailStatus);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(11));
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories(), hasSize(4));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getAttempts(), nullValue());

        restart = executionService.markAs(updateToFailStatus.withTaskRunIds(List.of(execution.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId())));

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
    void bulkMarkAs() throws Exception {
        Execution firstFail = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        Execution secondFail = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));

        assertThat(firstFail.findTaskRunByTaskIdAndValue("2-1-1_t1", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(secondFail.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));

        List<Execution> executions = executionService.bulkMarkAs(List.of(
            ExecutionService.UpdateStatusInputDto.builder()
                .execution(firstFail)
                .newState(State.Type.RUNNING)
                .build(),
            ExecutionService.UpdateStatusInputDto.builder()
                .execution(secondFail)
                .newState(State.Type.RUNNING)
                .build()
        ));

        assertThat(executionRepository.findByFlowId("io.kestra.tests", "restart-each", Pageable.from(1)).getTotal(), is(2L));
        assertThat(executions.get(0).findTaskRunByTaskIdAndValue("2-1-1_t1", List.of("value 1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(executions.get(1).findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.RUNNING));

        // However executions are not updated in database
        assertThat(executionRepository.findById(firstFail.getId()).get().findTaskRunByTaskIdAndValue("2-1-1_t1", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(executionRepository.findById(secondFail.getId()).get().findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    void bulkMarkAsIncorrectState_ShouldNotUpdateAnyExecution() throws Exception {
        Execution firstFail = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        Execution secondFail = runnerUtils.runOne("io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));

        assertThat(firstFail.findTaskRunByTaskIdAndValue("2-1-1_t1", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(secondFail.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));

        Execution runningSecondExecution = executionService.markAs(secondFail, secondFail.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId(), State.Type.RUNNING);

        Assertions.assertThrows(IllegalStateException.class, () -> executionService.bulkMarkAs(List.of(
            ExecutionService.UpdateStatusInputDto.builder()
                .execution(firstFail)
                .taskRunIds(List.of(firstFail.findTaskRunByTaskIdAndValue("2-1-1_t1", List.of("value 1")).getId()))
                .newState(State.Type.RUNNING)
                .build(),
            ExecutionService.UpdateStatusInputDto.builder()
                .execution(runningSecondExecution)
                .taskRunIds(List.of(runningSecondExecution.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId()))
                .newState(State.Type.RUNNING)
                .build()
        )));
    }
}