package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@KestraTest(startRunner = true)
class LazyOutputsMapTest {
    @Inject
    TestRunnerUtils runnerUtils;

    @Inject
    TaskOutputRepositoryInterface repo;

    @MockBean(TaskOutputRepositoryInterface.class)
    TaskOutputRepositoryInterface taskOutputRepositoryMock() {
        return mock(TaskOutputRepositoryInterface.class);
    }

    @Test
    @LoadFlows("flows/valids/per-key-output.yaml")
    void verifyReturn2NeverFetchedDuringRun() throws Exception {
        var outputsByTaskRunId = new ConcurrentHashMap<String, List<TaskOutput>>();
        when(repo.save(any(TaskOutput.class))).thenAnswer(inv -> {
            TaskOutput out = inv.getArgument(0);
            outputsByTaskRunId
                .computeIfAbsent(out.taskRunId(), k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(out);
            return out;
        });

        when(repo.findTaskIdWithOutputByExecution(any())).thenAnswer(inv -> {
            Execution e = inv.getArgument(0);
            if (e == null || e.getTaskRunList() == null) {
                return Set.of();
            }
            return e.getTaskRunList().stream().map(TaskRun::getTaskId).collect(Collectors.toCollection(LinkedHashSet::new));
        });

        when(repo.findByTaskRunIds(anyString(), anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> ids = inv.getArgument(1);
            if (ids != null && ids.contains("return2")) {
                throw new AssertionError("Unexpected fetch of outputs for return2");
            }
            List<TaskOutput> results = new ArrayList<>();
            if (ids != null) {
                for (String id : ids) {
                    List<TaskOutput> list = outputsByTaskRunId.get(id);
                    if (list != null) results.addAll(list);
                }
            }
            return results;
        });
        Execution execution = runnerUtils.runOne(TenantService.MAIN_TENANT, "io.kestra.tests", "per-key-output");

        Optional<TaskRun> trReturn1 = execution.getTaskRunList().stream().filter(tr -> "return1".equals(tr.getTaskId())).findFirst();
        Optional<TaskRun> trReturn2 = execution.getTaskRunList().stream().filter(tr -> "return2".equals(tr.getTaskId())).findFirst();

        assertThat(trReturn1).isPresent();
        assertThat(trReturn2).isPresent();

        String tr1Id = trReturn1.get().getId();
        String tr2Id = trReturn2.get().getId();

        verify(repo, atLeastOnce()).findTaskIdWithOutputByExecution(argThat(e -> e != null && Objects.equals(((Execution) e).getId(), execution.getId())));

        verify(repo, atLeastOnce()).findByTaskRunIds(anyString(), argThat(list -> list != null && list.contains(tr1Id)));

        verify(repo, never()).findByTaskRunIds(anyString(), argThat(list -> list != null && list.contains(tr2Id)));
    }
}
