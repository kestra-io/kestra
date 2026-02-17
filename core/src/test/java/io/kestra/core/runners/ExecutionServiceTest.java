package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Pause;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Slf4j
@KestraTest(startRunner = true)
class ExecutionServiceTest {

    public static final String TENANT_1 = "tenant1";
    public static final String TENANT_2 = "tenant2";
    public static final String TENANT_3 = "tenant3";
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    LogRepositoryInterface logRepository;

    @Inject
    TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartSimple() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(3);
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().get(2).getState().getHistories()).hasSize(5);
        assertThat(restart.getId()).isEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(2).getId()).isEqualTo(execution.getTaskRunList().get(2).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @Test
    @LoadFlows(value = {"flows/valids/restart_last_failed.yaml"}, tenantId = TENANT_1)
    void restartSimpleRevision() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        FlowWithSource flow = flowRepository.findByIdWithSource(TENANT_1, "io.kestra.tests", "restart_last_failed").orElseThrow();
        flowRepository.update(
            GenericFlow.of(flow),
            flow.updateTask(
                "a",
                Return.builder()
                    .id("a")
                    .type(Return.class.getName())
                    .format(Property.ofValue("replace"))
                    .build()
            )
        );


        Execution restart = executionService.restart(execution, 2);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(3);
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().get(2).getState().getHistories()).hasSize(5);
        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(2).getId()).isNotEqualTo(execution.getTaskRunList().get(2).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void restartFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count()).isGreaterThan(1L);

        assertThat(restart.getTaskRunList().getFirst().getId()).isEqualTo(restart.getTaskRunList().getFirst().getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @RetryingTest(5)
    @LoadFlows(value = {"flows/valids/restart-each.yaml"}, tenantId = TENANT_1)
    void restartFlowable2() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().getFirst().getId()).isEqualTo(restart.getTaskRunList().getFirst().getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    void restartDynamic() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory", null, (f, e) -> ImmutableMap.of("failed", "true"));
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.restart(execution, null);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);

        assertThat(restart.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().getFirst().getState().getHistories()).hasSize(4);
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/logs.yaml"})
    void replayFromBeginning() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, null, null);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getNamespace()).isEqualTo("io.kestra.tests");
        assertThat(restart.getFlowId()).isEqualTo("logs");

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(restart.getState().getHistories()).hasSize(1);
        assertThat(restart.getState().getHistories().getFirst().getDate(), not(is(execution.getState().getStartDate())));
        assertThat(restart.getTaskRunList()).hasSize(0);
        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows(value = {"flows/valids/logs.yaml"}, tenantId = TENANT_1)
    void replaySimple() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.getTaskRunList().get(1).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(2);
        assertThat(restart.getTaskRunList().get(1).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().get(1).getState().getHistories()).hasSize(5);
        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows(value = {"flows/valids/restart-each.yaml"}, tenantId = TENANT_2)
    void replayFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_2, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "NO"));
        assertThat(execution.getTaskRunList()).hasSize(20);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(20);
        assertThat(restart.getTaskRunList().get(19).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/parallel-nested.yaml"})
    void replayParallel() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "parallel-nested");
        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(8);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @ExecuteFlow(value = "flows/valids/each-sequential-nested.yaml", tenantId = TENANT_2)
    void replayEachSeq(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(23);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(5);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @ExecuteFlow(value = "flows/valids/each-sequential-nested.yaml", tenantId = TENANT_1)
    void replayEachSeq2(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(23);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("1-2-1_return", List.of("s1", "a a")).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(6);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("1-2_each", List.of("s1")).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/dynamic-task.yaml"})
    void replayWithADynamicTask() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "dynamic-task");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.getTaskRunList().get(2).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(3);
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().get(2).getState().getHistories()).hasSize(5);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/each-parallel-nested.yaml"})
    void replayEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "each-parallel-nested");
        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId(), null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(8);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows(value = {"flows/valids/each-parallel-nested.yaml"}, tenantId = TENANT_1)
    void markAsEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "each-parallel-nested");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.markAs(execution, flow, execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getMetadata().getAttemptNumber()).isEqualTo(2);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(11);
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories()).hasSize(4);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        restart = executionService.markAs(execution, flow, execution.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(11);
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getHistories()).hasSize(5);
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void resumePausedToRunning() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause-test");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

        Execution resume = executionService.resume(execution, flow, State.Type.RUNNING, Pause.Resumed.now());

        assertThat(resume.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(resume.getState().getHistories()).hasSize(4);

        assertThrows(
            IllegalArgumentException.class,
            () -> executionService.resume(resume, flow, State.Type.RUNNING, Pause.Resumed.now())
        );
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause-test.yaml"}, tenantId = TENANT_1)
    void resumePausedToKilling() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(TENANT_1, "io.kestra.tests", "pause-test");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

        Execution resume = executionService.resume(execution, flow, State.Type.KILLING, null);

        assertThat(resume.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(resume.getState().getHistories()).hasSize(4);
    }

    @Test
    @ExecuteFlow(value = "flows/valids/logs.yaml", tenantId = TENANT_2)
    void deleteExecution(Execution execution) throws IOException, TimeoutException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Await.until(() -> logRepository.findByExecutionId(execution.getTenantId(), execution.getId(), Level.TRACE).size() == 5, Duration.ofMillis(10), Duration.ofSeconds(5));

        executionService.delete(execution, true, true, true);

        assertThat(executionRepository.findById(execution.getTenantId(), execution.getId())).isEqualTo(Optional.empty());
        assertThat(logRepository.findByExecutionId(execution.getTenantId(), execution.getId(), Level.INFO)).isEmpty();
    }

    @Test
    @ExecuteFlow(value = "flows/valids/logs.yaml", tenantId = TENANT_3)
    void deleteExecutionKeepLogs(Execution execution) throws IOException, TimeoutException {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Await.until(() -> logRepository.findByExecutionId(execution.getTenantId(), execution.getId(), Level.TRACE).size() == 5, Duration.ofMillis(10), Duration.ofSeconds(5));

        executionService.delete(execution, false, false, false);

        assertThat(executionRepository.findById(execution.getTenantId(), execution.getId())).isEqualTo(Optional.empty());
        assertThat(logRepository.findByExecutionId(execution.getTenantId(), execution.getId(), Level.INFO)).hasSize(4);
    }

    @Test
    @LoadFlows({"flows/valids/pause_no_tasks.yaml"})
    void shouldKillPausedExecutions() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "pause_no_tasks");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.PAUSED);

        Execution killed = executionService.kill(execution, flow);

        assertThat(killed.getState().getCurrent()).isEqualTo(State.Type.KILLING);
        assertThat(killed.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killed.getState().getHistories()).hasSize(5);
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void shouldRestartAfterChangeTaskState(Execution execution) throws Exception {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution markedAs = executionService.markAs(execution, flow, execution.getTaskRunList().getFirst().getId(), State.Type.SUCCESS);
        assertThat(markedAs.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
    }

    @Test
    @LoadFlows(value = {"flows/valids/pause_no_tasks.yaml"}, tenantId = TENANT_1)
    void killToState() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(TENANT_1, "io.kestra.tests", "pause_no_tasks");
        Flow flow = flowRepository.findByExecution(execution);

        Execution killed = executionService.kill(execution, flow, Optional.of(State.Type.CANCELLED));

        assertThat(killed.getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
        assertThat(killed.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killed.findTaskRunsByTaskId("pause").getFirst().getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(killed.getState().getHistories()).hasSize(5);
    }

    @Test
    @LoadFlows({"flows/valids/change-state-errors.yaml"})
    void changeStateWithErrorBranch() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "change-state-errors");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Execution restart = executionService.changeTaskRunState(execution, flow, execution.findTaskRunsByTaskId("make_error").getFirst().getId(), State.Type.SUCCESS);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getMetadata().getAttemptNumber()).isEqualTo(2);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(2);
        assertThat(restart.findTaskRunsByTaskId("make_error").getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/each-pause.yaml"})
    void killExecutionWithFlowableTask() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "each-pause");

        TaskRun childTaskRun = execution.getTaskRunList().stream().filter(tr -> tr.getTaskId().equals("pause")).toList().getFirst();

        Execution killed = executionService.killParentTaskruns(childTaskRun,execution);

        TaskRun parentTaskRun = killed.getTaskRunList().stream().filter(tr -> tr.getTaskId().equals("each_task")).toList().getFirst();

        assertThat(parentTaskRun.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(parentTaskRun.getAttempts().getLast().getState().getCurrent()).isEqualTo(State.Type.KILLED);

    }
}