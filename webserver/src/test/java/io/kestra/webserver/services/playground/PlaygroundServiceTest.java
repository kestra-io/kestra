package io.kestra.webserver.services.playground;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.playground.PlaygroundService.ReusedTaskRun;

import jakarta.inject.Inject;

import static io.kestra.webserver.services.playground.PlaygroundService.RETAINED_RUNS;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PlaygroundServiceTest {
    private static final String NAMESPACE = "io.kestra.tests";
    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

    @Inject
    private PlaygroundService playgroundService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void shouldKeepTheNewestTerminatedRunsOfTheSameUserWhenANewRunStarts() {
        String tenantId = IdUtils.create().toLowerCase();
        List<Execution> runs = new ArrayList<>();
        for (int i = 0; i < RETAINED_RUNS + 2; i++) {
            runs.add(save(run(tenantId, "alice", i, State.Type.KILLED)));
        }
        Execution stillRunning = save(run(tenantId, "alice", -1, State.Type.RUNNING));
        Execution otherUser = save(run(tenantId, "bob", -2, State.Type.KILLED));
        Execution newRun = run(tenantId, "alice", RETAINED_RUNS + 10, State.Type.CREATED);

        playgroundService.onNewRun(newRun);

        assertThat(executionRepository.findById(tenantId, runs.get(0).getId())).isEmpty();
        assertThat(executionRepository.findById(tenantId, runs.get(1).getId())).isEmpty();
        assertThat(executionRepository.findById(tenantId, runs.get(2).getId())).isEmpty();
        assertThat(runs.subList(3, runs.size())).allMatch(kept -> executionRepository.findById(tenantId, kept.getId()).isPresent());
        assertThat(executionRepository.findById(tenantId, stillRunning.getId())).as("a run in progress is never purged").isPresent();
        assertThat(executionRepository.findById(tenantId, otherUser.getId())).as("another user's runs are counted apart").isPresent();
    }

    @Test
    void shouldKeepTheFilesOfAPurgedRunWhenAKeptRunStillPointsAtThem() throws Exception {
        String tenantId = IdUtils.create().toLowerCase();
        Execution producer = save(run(tenantId, "alice", 0, State.Type.KILLED));
        Execution unreferenced = save(run(tenantId, "alice", 1, State.Type.KILLED));
        URI producerFile = putFile(producer);
        URI unreferencedFile = putFile(unreferenced);
        for (int i = 2; i < RETAINED_RUNS + 1; i++) {
            save(run(tenantId, "alice", i, State.Type.KILLED));
        }
        Execution newRun = run(tenantId, "alice", RETAINED_RUNS + 1, State.Type.CREATED);
        taskOutputRepository.save(new TaskOutput(IdUtils.create(), tenantId, newRun.getId(), null, producerFile.toString()));

        playgroundService.onNewRun(newRun);

        assertThat(executionRepository.findById(tenantId, producer.getId())).isEmpty();
        assertThat(storageInterface.exists(tenantId, NAMESPACE, producerFile)).isTrue();
        assertThat(storageInterface.exists(tenantId, NAMESPACE, unreferencedFile)).isFalse();
    }

    @Test
    void shouldPointAReusedTaskRunAtTheRunThatFirstProducedItAcrossChainedReplays() {
        String tenantId = IdUtils.create().toLowerCase();
        State ranOnce = State.of(State.Type.SUCCESS, List.of(new State.History(State.Type.CREATED, NOW), new State.History(State.Type.SUCCESS, NOW.plusSeconds(1))));
        Execution first = save(
            runBuilder(tenantId, "alice", 0, State.Type.KILLED)
                .taskRunList(List.of(taskRun("a1", "a", ranOnce), taskRun("b1", "b", new State(State.Type.KILLED)))).build()
        );
        Execution second = save(
            runBuilder(tenantId, "alice", 1, State.Type.KILLED).parentId(first.getId())
                .taskRunList(List.of(taskRun("a2", "a", ranOnce), taskRun("b2", "b", new State(State.Type.SUCCESS)))).build()
        );
        Execution third = save(
            runBuilder(tenantId, "alice", 2, State.Type.KILLED).parentId(second.getId())
                .taskRunList(List.of(taskRun("a3", "a", ranOnce), taskRun("b3", "b", new State(State.Type.SUCCESS)))).build()
        );

        Map<String, ReusedTaskRun> reused = playgroundService.reused(third);

        assertThat(reused).containsEntry("a3", new ReusedTaskRun(first.getId(), "a1"));
        assertThat(reused).as("b ran again in the second run, whose state history differs from the third's").doesNotContainKey("b3");
    }

    private Execution save(Execution execution) {
        return executionRepository.save(execution);
    }

    private URI putFile(Execution execution) throws Exception {
        URI uri = URI.create(StorageContext.forExecution(execution).getExecutionStorageURI(StorageContext.KESTRA_SCHEME) + "/a/file.txt");
        return storageInterface.put(execution.getTenantId(), NAMESPACE, uri, new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8)));
    }

    private static TaskRun taskRun(String id, String taskId, State state) {
        return TaskRun.builder().id(id).namespace(NAMESPACE).flowId("playground").taskId(taskId).state(state).build();
    }

    private static Execution run(String tenantId, String username, int minutes, State.Type state) {
        return runBuilder(tenantId, username, minutes, state).build();
    }

    private static Execution.ExecutionBuilder runBuilder(String tenantId, String username, int minutes, State.Type state) {
        Instant start = NOW.plusSeconds(60L * minutes);
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenantId)
            .namespace(NAMESPACE)
            .flowId("playground")
            .flowRevision(1)
            .kind(ExecutionKind.PLAYGROUND)
            .labels(List.of(new Label(Label.USERNAME, username)))
            .state(State.of(state, List.of(new State.History(State.Type.CREATED, start), new State.History(state, start.plusSeconds(1)))));
    }
}
