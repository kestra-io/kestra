package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.plugin.core.debug.Return;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
class ExecutionServiceTest {
    @Inject
    ExecutionService executionService;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    PluginDefaultService pluginDefaultService;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    @Inject
    LogRepositoryInterface logRepository;

    @Inject
    RunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartSimple() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "restart_last_failed");
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
        assertThat(restart.getLabels(), hasItem(new Label(Label.RESTARTED, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartSimpleRevision() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "restart_last_failed");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        FlowWithSource flow = flowRepository.findByIdWithSource(null, "io.kestra.tests", "restart_last_failed").orElseThrow();
        flowRepository.update(
            flow,
            flow.updateTask(
                "a",
                Return.builder()
                    .id("a")
                    .type(Return.class.getName())
                    .format(Property.of("replace"))
                    .build()
            ),
            JacksonMapper.ofYaml().writeValueAsString(flow),
            pluginDefaultService.injectDefaults(flow)
        );


        Execution restart = executionService.restart(execution, 2);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));
        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(2).getId(), not(execution.getTaskRunList().get(2).getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.RESTARTED, "true")));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void restartFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "FIRST"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().getFirst().getId(), is(restart.getTaskRunList().getFirst().getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.RESTARTED, "true")));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void restartFlowable2() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "SECOND"));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RESTARTED).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().stream().filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING).count(), greaterThan(1L));
        assertThat(restart.getTaskRunList().getFirst().getId(), is(restart.getTaskRunList().getFirst().getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.RESTARTED, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/working-directory.yaml"})
    void restartDynamic() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "working-directory", null, (f, e) -> ImmutableMap.of("failed", "true"));
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));

        Execution restart = executionService.restart(execution, null);
        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));

        assertThat(restart.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().getFirst().getState().getHistories(), hasSize(4));
        assertThat(restart.getLabels(), hasItem(new Label(Label.RESTARTED, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/logs.yaml"})
    void replayFromBeginning() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, null, null);

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getNamespace(), is("io.kestra.tests"));
        assertThat(restart.getFlowId(), is("logs"));

        assertThat(restart.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(restart.getState().getHistories(), hasSize(1));
        assertThat(restart.getState().getHistories().getFirst().getDate(), not(is(execution.getState().getStartDate())));
        assertThat(restart.getTaskRunList(), hasSize(0));
        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/logs.yaml"})
    void replaySimple() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "logs");
        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.getTaskRunList().get(1).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(2));
        assertThat(restart.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(1).getState().getHistories(), hasSize(4));
        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void replayFlowable() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "restart-each", null, (f, e) -> ImmutableMap.of("failed", "NO"));
        assertThat(execution.getTaskRunList(), hasSize(20));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.findTaskRunByTaskIdAndValue("2_end", List.of()).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(20));
        assertThat(restart.getTaskRunList().get(19).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/parallel-nested.yaml"})
    void replayParallel() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "parallel-nested");
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
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/each-sequential-nested.yaml"})
    void replayEachSeq() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested");
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
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/each-sequential-nested.yaml"})
    void replayEachSeq2() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested");
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
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/dynamic-task.yaml"})
    void replayWithADynamicTask() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "dynamic-task");
        assertThat(execution.getTaskRunList(), hasSize(3));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.replay(execution, execution.getTaskRunList().get(2).getId(), null);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(3));
        assertThat(restart.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getTaskRunList().get(2).getState().getHistories(), hasSize(4));

        assertThat(restart.getId(), not(execution.getId()));
        assertThat(restart.getTaskRunList().get(1).getId(), not(execution.getTaskRunList().get(1).getId()));
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/each-parallel-nested.yaml"})
    void replayEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-nested");
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
        assertThat(restart.getLabels(), hasItem(new Label(Label.REPLAY, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/each-parallel-nested.yaml"})
    void markAsEachPara() throws Exception {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-parallel-nested");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList(), hasSize(11));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution restart = executionService.markAs(execution, flow, execution.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getMetadata().getAttemptNumber(), is(2));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(11));
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getHistories(), hasSize(4));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getAttempts(), nullValue());

        restart = executionService.markAs(execution, flow, execution.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getId(), State.Type.FAILED);

        assertThat(restart.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(restart.getState().getHistories(), hasSize(4));
        assertThat(restart.getTaskRunList(), hasSize(11));
        assertThat(restart.findTaskRunByTaskIdAndValue("1_each", List.of()).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1_seq", List.of("value 1")).getState().getCurrent(), is(State.Type.RUNNING));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getState().getHistories(), hasSize(4));
        assertThat(restart.findTaskRunByTaskIdAndValue("2-1-2_t2", List.of("value 1")).getAttempts().getFirst().getState().getCurrent(), is(State.Type.FAILED));
    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void resumePausedToRunning() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

        Execution resume = executionService.resume(execution, flow, State.Type.RUNNING);

        assertThat(resume.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(resume.getState().getHistories(), hasSize(4));

        assertThrows(
            IllegalArgumentException.class,
            () -> executionService.resume(resume, flow, State.Type.RUNNING)
        );
    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void resumePausedToKilling() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

        Execution resume = executionService.resume(execution, flow, State.Type.KILLING);

        assertThat(resume.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(resume.getState().getHistories(), hasSize(4));
    }

    @Test
    @ExecuteFlow("flows/valids/logs.yaml")
    void deleteExecution(Execution execution) throws IOException {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        executionService.delete(execution, true, true, true);

        assertThat(executionRepository.findById(execution.getTenantId(),execution.getId()), is(Optional.empty()));
        assertThat(logRepository.findByExecutionId(execution.getTenantId(),execution.getId(), Level.INFO), hasSize(0));
    }

    @Test
    @ExecuteFlow("flows/valids/logs.yaml")
    void deleteExecutionKeepLogs(Execution execution) throws IOException {
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        executionService.delete(execution, false, false, false);

        assertThat(executionRepository.findById(execution.getTenantId(),execution.getId()), is(Optional.empty()));
        assertThat(logRepository.findByExecutionId(execution.getTenantId(),execution.getId(), Level.INFO), hasSize(4));
    }

    @Test
    @LoadFlows({"flows/valids/pause_no_tasks.yaml"})
    void shouldKillPausedExecutions() throws Exception {
        Execution execution = runnerUtils.runOneUntilPaused(null, "io.kestra.tests", "pause_no_tasks");
        Flow flow = flowRepository.findByExecution(execution);

        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getState().getCurrent(), is(State.Type.PAUSED));

        Execution killed = executionService.kill(execution, flow);

        assertThat(killed.getState().getCurrent(), is(State.Type.RESTARTED));
        assertThat(killed.findTaskRunsByTaskId("pause").getFirst().getState().getCurrent(), is(State.Type.KILLED));
        assertThat(killed.getState().getHistories(), hasSize(4));
    }

    @Test
    @ExecuteFlow("flows/valids/failed-first.yaml")
    void shouldRestartAfterChangeTaskState(Execution execution) throws Exception {
        assertThat(execution.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(execution.getTaskRunList(), hasSize(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.FAILED));

        Flow flow = flowRepository.findByExecution(execution);
        Execution markedAs = executionService.markAs(execution, flow, execution.getTaskRunList().getFirst().getId(), State.Type.SUCCESS);
        assertThat(markedAs.getState().getCurrent(), is(State.Type.RESTARTED));
    }
}