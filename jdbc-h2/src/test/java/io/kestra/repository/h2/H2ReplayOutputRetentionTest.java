package io.kestra.repository.h2;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.services.TaskOutputService;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.debug.Return;

import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
@Property(name = "kestra.task.outputs.limit", value = "256")
class H2ReplayOutputRetentionTest {
    @Inject
    TestRunnerUtils runner;
    @Inject
    ExecutionService executions;
    @Inject
    ExecutionRepositoryInterface executionRepository;
    @Inject
    FlowRepositoryInterface flowRepository;
    @Inject
    TaskOutputService outputs;
    @Inject
    TaskOutputRepositoryInterface outputRepository;
    @Inject
    StorageInterface storage;
    @Inject
    RunContextFactory contexts;

    private final List<Execution> fixtures = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        for (Execution execution : fixtures) {
            storage.deleteByPrefix(execution.getTenantId(), execution.getNamespace(), StorageContext.forExecution(execution).getExecutionStorageURI());
        }
        if (!fixtures.isEmpty()) {
            outputs.purge(fixtures);
            executionRepository.purge(fixtures);
        }
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldPreservePartialReplayOutputsWhenOriginalIsDeleted(String tenant) throws Exception {
        Execution original = runOriginal(tenant, false);
        Flow flow = flowRepository.findByExecution(original);
        Map<String, Object> expected = outputs.getOutputs(task(original, "first"));
        String originalUri = output(task(original, "first")).uri();
        Execution replay = replay(original, flow);

        executions.delete(original, false, false, true);

        assertThat(storage.exists(tenant, flow.getNamespace(), URI.create(originalUri))).isFalse();
        assertRetainedOutputs(flow, replay, expected);
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldPreservePartialReplayOutputsWhenOnlyOriginalIsPurged(String tenant) throws Exception {
        Execution original = runOriginal(tenant, false);
        Flow flow = flowRepository.findByExecution(original);
        Map<String, Object> expected = outputs.getOutputs(task(original, "first"));
        Instant old = Instant.now().minusSeconds(172800);
        original = original.toBuilder().state(
            State.of(
                State.Type.SUCCESS, List.of(
                    new State.History(State.Type.CREATED, old.minusSeconds(1)),
                    new State.History(State.Type.SUCCESS, old)
                )
            )
        ).build();
        executionRepository.save(original);
        int originalOutputCount = outputRepository.findByExecution(original).size();
        Execution replay = replay(original, flow);

        var purged = executions.purge(
            true, false, false, true, tenant, flow.getNamespace(), flow.getId(), null,
            ZonedDateTime.ofInstant(Instant.now().minusSeconds(3600), ZoneOffset.UTC), null, 100
        );

        assertThat(purged.getExecutionsCount()).isEqualTo(1);
        assertThat(purged.getTaskOutputsCount()).isEqualTo(originalOutputCount);
        assertThat(executionRepository.findById(tenant, original.getId())).isEmpty();
        assertThat(outputRepository.findById(tenant, task(original, "first").getId())).isEmpty();
        assertRetainedOutputs(flow, replay, expected);
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldCreateFreshOutputsWhenReplayingAfterOriginalStorageIsDeleted(String tenant) throws Exception {
        Execution original = runOriginal(tenant, false);
        Flow flow = flowRepository.findByExecution(original);
        Map<String, Object> expected = outputs.getOutputs(task(original, "first"));
        executions.delete(original, false, false, true);

        Execution replay = executions.replay(original, flow, null, null, Optional.empty());
        assertThat(replay.getTaskRunList()).isEmpty();
        replay = run(replay, flow);

        assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertRetainedOutputs(flow, replay, expected);
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldReplayWhenDiscardedDownstreamBlobIsMissing(String tenant) throws Exception {
        Execution original = runOriginal(tenant, false);
        Flow flow = flowRepository.findByExecution(original);
        Map<String, Object> expected = outputs.getOutputs(task(original, "first"));
        TaskRun discarded = task(original, "third");
        storage.delete(tenant, flow.getNamespace(), URI.create(output(discarded).uri()));

        Execution replay = executions.replay(original, flow, task(original, "second").getId(), null, Optional.empty());

        assertThat(replay.getTaskRunList()).noneMatch(taskRun -> taskRun.getTaskId().equals("third"));
        assertThat(outputRepository.findByExecution(replay)).hasSize(replay.getTaskRunList().size());
        replay = run(replay, flow);
        assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertRetainedOutputs(flow, replay, expected);
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldPreserveRevisionRestartOutputsWhenOriginalIsDeleted(String tenant) throws Exception {
        Execution original = runOriginal(tenant, true);
        Flow flow = revisionWithoutFailure(flowRepository.findByExecution(original));
        Map<String, Object> expected = outputs.getOutputs(task(original, "first"));
        Execution restart = run(executions.restart(original, flow, flow.getRevision()), flow);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        executions.delete(original, false, false, true);

        assertRetainedOutputs(flow, restart, expected);
    }

    @Test
    @LoadFlowsWithTenant("flows/valids/replay-output-retention.yaml")
    void shouldRestartWhenRemovedFinallyBlobIsMissing(String tenant) throws Exception {
        Execution original = runOriginal(tenant, true);
        Flow flow = revisionWithoutFailure(flowRepository.findByExecution(original));
        TaskRun cleanup = task(original, "cleanup");
        storage.delete(tenant, flow.getNamespace(), URI.create(output(cleanup).uri()));

        Execution restart = executions.restart(original, flow, flow.getRevision());

        assertThat(restart.getTaskRunList()).noneMatch(taskRun -> taskRun.getTaskId().equals("cleanup"));
        assertThat(outputRepository.findByExecution(restart)).hasSize(1);
        restart = run(restart, flow);
        assertThat(restart.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    private Flow revisionWithoutFailure(Flow flow) {
        List<Task> tasks = new ArrayList<>(flow.getTasks());
        tasks.set(
            1, Return.builder()
                .id(tasks.get(1).getId())
                .type(Return.class.getName())
                .format(io.kestra.core.models.property.Property.ofValue("{{ outputs.first.value | length }}"))
                .build()
        );
        return flowRepository.update(GenericFlow.of(flow.toBuilder().tasks(tasks).build()), flow);
    }

    private Execution runOriginal(String tenant, boolean fail) throws Exception {
        Execution execution = runner.runOne(
            tenant, "io.kestra.tests", "replay-output-retention", null,
            (flow, current) -> Map.of("fail", fail), Duration.ofSeconds(45)
        );
        fixtures.add(execution);
        assertThat(execution.getState().getCurrent()).isEqualTo(fail ? State.Type.FAILED : State.Type.SUCCESS);
        assertThat(output(task(execution, "first")).value()).isNull();
        assertThat(output(task(execution, "first")).uri()).isNotNull();
        return execution;
    }

    private Execution replay(Execution original, Flow flow) throws Exception {
        Execution replay = run(executions.replay(original, flow, task(original, "second").getId(), null, Optional.empty()), flow);
        assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        return replay;
    }

    private Execution run(Execution execution, Flow flow) throws Exception {
        fixtures.add(execution);
        return runner.runOne(execution, flow, Duration.ofSeconds(45));
    }

    private void assertRetainedOutputs(Flow flow, Execution execution, Map<String, Object> expected) throws Exception {
        TaskRun first = task(execution, "first");
        assertThat(executionRepository.findById(execution.getTenantId(), execution.getId())).isPresent();
        assertThat(URI.create(output(first).uri()).getPath()).startsWith(StorageContext.forTask(first).getContextStorageURI().getPath() + "/");
        assertThat(outputs.getOutputs(first)).isEqualTo(expected);
        contexts.of(flow, execution);
    }

    private TaskRun task(Execution execution, String id) {
        return execution.findTaskRunsByTaskId(id).getFirst();
    }

    private TaskOutput output(TaskRun taskRun) {
        return outputRepository.findById(taskRun.getTenantId(), taskRun.getId()).orElseThrow();
    }
}
