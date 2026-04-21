package io.kestra.core.runners;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.debug.Breakpoint;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.utils.Await;
import io.kestra.plugin.core.flow.Pause;

import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

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
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void restartSimple() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.restart(execution, flow, null);

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
    @LoadFlows(value = { "flows/valids/restart_last_failed.yaml" }, tenantId = TENANT_1)
    void restartSimpleRevision() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        String updatedSource = """
            id: restart_last_failed
            namespace: io.kestra.tests

            tasks:
            - id: a
              type: io.kestra.plugin.core.debug.Return
              format: replace
            - id: b
              type: io.kestra.plugin.core.log.Log
              message: "{{ task.id }}"
            - id: c
              type: io.kestra.plugin.core.log.Log
              message: "{{taskrun.attemptsCount == 1 ? 'ok' : ko}}"
            - id: d
              type: io.kestra.plugin.core.log.Log
              message: "{{ task.id }}\"""";
        Flow flow = YamlParser.parse(updatedSource, Flow.class);
        flowRepository.update(
            GenericFlow.of(flow),
            flow
        );

        Execution restart = executionService.restart(execution, flow, 2);

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
    @LoadFlows({ "flows/valids/restart-each.yaml" })
    void restartFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.restart(execution, flow, null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count()).isGreaterThan(1L);

        assertThat(restart.getTaskRunList().getFirst().getId()).isEqualTo(restart.getTaskRunList().getFirst().getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @RetryingTest(5)
    @LoadFlows(value = { "flows/valids/restart-each.yaml" }, tenantId = TENANT_1)
    void restartFlowable2() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.restart(execution, flow, null);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count()).isGreaterThan(1L);
        assertThat(restart.getTaskRunList().getFirst().getId()).isEqualTo(restart.getTaskRunList().getFirst().getId());
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @Test
    @LoadFlows({ "flows/valids/working-directory.yaml" })
    void restartDynamic() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "working-directory", null, (f, e) -> ImmutableMap.of("failed", "true"));
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.restart(execution, flow, null);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);

        assertThat(restart.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getTaskRunList().getFirst().getState().getHistories()).hasSize(4);
        assertThat(restart.getLabels()).contains(new Label(Label.RESTARTED, "true"));
    }

    @Test
    @LoadFlows({ "flows/valids/logs.yaml" })
    void replayFromBeginning() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, null, null, Optional.empty());

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
    @LoadFlows(value = { "flows/valids/logs.yaml" }, tenantId = TENANT_1)
    void replaySimple() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.getTaskRunList().get(1).getId(), null, Optional.empty());

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
    @LoadFlows(value = { "flows/valids/restart-each.yaml" }, tenantId = TENANT_2)
    void replayFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_2, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "NO"));
        assertThat(execution.getTaskRunList()).hasSize(20);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null, Optional.empty());

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(20);
        assertThat(restart.getTaskRunList().get(19).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows({ "flows/valids/parallel-nested.yaml" })
    void replayParallel() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "parallel-nested");
        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.findTaskRunByTaskIdAndValue("1-3-2_par", List.of()).getId(), null, Optional.empty());

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
    @LoadFlows({ "flows/valids/parallel-nested.yaml" })
    void replayParallelRestartsRunningSibling() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "parallel-nested");
        assertThat(execution.getTaskRunList()).hasSize(11);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        TaskRun replayTarget = execution.findTaskRunByTaskIdAndValue("1-3-2_par", List.of());
        TaskRun runningSibling = execution.findTaskRunByTaskIdAndValue("1-3-3_end", List.of());

        Execution executionWithRunningSibling = execution.withTaskRunList(
            execution.getTaskRunList()
                .stream()
                .map(
                    taskRun -> taskRun.getId().equals(runningSibling.getId())
                        ? taskRun.withState(State.Type.RUNNING)
                        : taskRun
                )
                .toList()
        );

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(executionWithRunningSibling, flow, replayTarget.getId(), null, Optional.empty());

        TaskRun restartedSibling = restart.findTaskRunByTaskIdAndValue("1-3-3_end", List.of());
        assertThat(restartedSibling.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restartedSibling.getState().getHistories().stream().anyMatch(history -> history.getState() == State.Type.RESTARTED)).isTrue();
        assertThat(restartedSibling.getId()).isNotEqualTo(runningSibling.getId());
        assertThat(restartedSibling.getAttempts()).hasSize(runningSibling.getAttempts().size() + 1);
        assertThat(restartedSibling.lastAttempt().getState().getCurrent()).isEqualTo(State.Type.RESUBMITTED);
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-nested.yaml", tenantId = TENANT_2)
    void replayEachSeq(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(16);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.findTaskRunByTaskIdAndValue("each1", List.of("l1")).getId(), null, Optional.empty());

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(2);
        assertThat(restart.findTaskRunByTaskIdAndValue("each1", List.of("l1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("each1", List.of("l1")).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @ExecuteFlow(value = "flows/valids/foreach-nested.yaml", tenantId = TENANT_1)
    void replayEachSeq2(Execution execution) throws Exception {
        assertThat(execution.getTaskRunList()).hasSize(16);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.findTaskRunByTaskIdAndValue("p1", List.of("l1", "d1")).getId(), null, Optional.empty());

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(3);
        assertThat(restart.findTaskRunByTaskIdAndValue("each1", List.of("l1")).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("each1", List.of("l1")).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows({ "flows/valids/dynamic-task.yaml" })
    void replayWithADynamicTask() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "dynamic-task");
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.getTaskRunList().get(2).getId(), null, Optional.empty());

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
    @LoadFlows({ "flows/valids/foreach-concurrent-no-limit.yaml" })
    void replayEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "foreach-concurrent-no-limit");
        assertThat(execution.getTaskRunList()).hasSize(7);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Flow flow = flowRepository.findByExecution(execution);
        Execution restart = executionService.replay(execution, flow, execution.findTaskRunByTaskIdAndValue("log", List.of("value 1")).getId(), null, Optional.empty());

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(2);
        assertThat(restart.findTaskRunByTaskIdAndValue("for_each", List.of()).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("for_each", List.of()).getState().getHistories()).hasSize(4);

        assertThat(restart.getId()).isNotEqualTo(execution.getId());
        assertThat(restart.getTaskRunList().get(1).getId()).isNotEqualTo(execution.getTaskRunList().get(1).getId());
        assertThat(restart.getLabels()).contains(new Label(Label.REPLAY, "true"));
    }

    @Test
    @LoadFlows(value = { "flows/valids/foreach-concurrent-no-limit.yaml" }, tenantId = TENANT_1)
    void markAsEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(TENANT_1, "io.kestra.tests", "foreach-concurrent-no-limit");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList()).hasSize(7);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution restart = executionService.markAs(execution, flow, execution.findTaskRunByTaskIdAndValue("log", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(restart.getMetadata().getAttemptNumber()).isEqualTo(2);
        assertThat(restart.getState().getHistories()).hasSize(4);
        assertThat(restart.getTaskRunList()).hasSize(7);
        assertThat(restart.findTaskRunByTaskIdAndValue("for_each", List.of()).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(restart.findTaskRunByTaskIdAndValue("log", List.of("value 1")).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(restart.findTaskRunByTaskIdAndValue("log", List.of("value 1")).getState().getHistories()).hasSize(5);
    }

    @Test
    @LoadFlows({ "flows/valids/pause-test.yaml" })
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
    @LoadFlows(value = { "flows/valids/pause-test.yaml" }, tenantId = TENANT_1)
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
    @LoadFlows({ "flows/valids/pause_no_tasks.yaml" })
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
    @LoadFlows(value = { "flows/valids/pause_no_tasks.yaml" }, tenantId = TENANT_1)
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
    @LoadFlows({ "flows/valids/change-state-errors.yaml" })
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
    @LoadFlows({ "flows/valids/replay-sequential-with-error-handler.yaml" })
    void replaySequentialWithErrorHandler() throws Exception {
        // Given: run the flow — failing-task fails, error-handler runs, sequential fails
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "replay-sequential-with-error-handler");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        // task runs: before, sequential, failing-task, error-handler = 4
        assertThat(execution.getTaskRunList()).hasSize(4);

        String sequentialTaskRunId = execution.findTaskRunByTaskIdAndValue("sequential", List.of()).getId();

        // When: replay from the parent Sequential task
        Flow flow = flowRepository.findByExecution(execution);
        Execution replay = executionService.replay(execution, flow, sequentialTaskRunId, null, Optional.empty());

        // Then: the stale error-handler task run must be removed
        assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
        assertThat(replay.getTaskRunList()).hasSize(2); // before + sequential only
        assertThat(replay.findTaskRunByTaskIdAndValue("sequential", List.of()).getState().getCurrent())
            .isEqualTo(State.Type.RUNNING);
        assertThat(
            replay.getTaskRunList().stream()
                .noneMatch(tr -> tr.getTaskId().equals("error-handler"))
        ).isTrue();

        // And: the replayed execution should terminate (not hang indefinitely in RUNNING)
        Execution result = runnerUtils.emitAndAwaitExecution(
            e -> e.getId().equals(replay.getId()) && e.getState().isTerminated(),
            replay,
            Duration.ofSeconds(30)
        );
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    @Test
    @LoadFlows({ "flows/valids/each-pause.yaml" })
    void killExecutionWithFlowableTask() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(MAIN_TENANT, "io.kestra.tests", "each-pause");

        TaskRun childTaskRun = execution.getTaskRunList().stream().filter(tr -> tr.getTaskId().equals("pause")).toList().getFirst();

        Execution killed = executionService.killParentTaskruns(childTaskRun, execution);

        TaskRun parentTaskRun = killed.getTaskRunList().stream().filter(tr -> tr.getTaskId().equals("each_task")).toList().getFirst();

        assertThat(parentTaskRun.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(parentTaskRun.getAttempts().getLast().getState().getCurrent()).isEqualTo(State.Type.KILLED);

    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void shouldResumeFromBreakpoint() {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withBreakpoints(List.of(Breakpoint.of("date")))
            .withTaskRunList(
                List.of(
                    TaskRun.builder()
                        .id("taskrun")
                        .state(new State(State.Type.BREAKPOINT))
                        .build()
                )
            )
            .withState(State.Type.BREAKPOINT);

        Execution resumed = executionService.resumeFromBreakpoint(execution, Optional.empty());

        assertThat(resumed.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
        assertThat(resumed.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void resumeFromBreakpointShouldThrowWhenNotSuspended() {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withBreakpoints(List.of(Breakpoint.of("date")))
            .withTaskRunList(
                List.of(
                    TaskRun.builder()
                        .id("taskrun")
                        .state(new State(State.Type.BREAKPOINT))
                        .build()
                )
            )
            .withState(State.Type.CREATED);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> executionService.resumeFromBreakpoint(execution, Optional.empty()));
        assertThat(error.getMessage()).isEqualTo("Execution is not suspended");
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void resumeFromBreakpointShouldThrowWhenNoBreakpoints() {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withTaskRunList(
                List.of(
                    TaskRun.builder()
                        .id("taskrun")
                        .state(new State(State.Type.BREAKPOINT))
                        .build()
                )
            )
            .withState(State.Type.BREAKPOINT);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> executionService.resumeFromBreakpoint(execution, Optional.empty()));
        assertThat(error.getMessage()).isEqualTo("Execution has no breakpoint");
    }

    @Test
    @ExecuteFlow("flows/valids/minimal.yaml")
    void changeState(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);

        Execution newExecution = executionService.changeState(execution, State.Type.WARNING);
        assertThat(newExecution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void unqueue() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withState(State.Type.QUEUED);

        Execution newExecution = executionService.unqueue(execution, State.Type.RUNNING);
        assertThat(newExecution.getState().getCurrent()).isEqualTo(State.Type.RUNNING);
    }

    @Test
    @LoadFlows("flows/valids/minimal.yaml")
    void updateLabels() throws Exception {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList())
            .withState(State.Type.QUEUED);

        List<Label> labels = execution.getLabels();
        labels.add(new Label("test", "test"));
        Execution newExecution = executionService.updateLabels(execution, labels);
        assertThat(newExecution.getLabels()).contains(new Label("test", "test"));
    }
}
