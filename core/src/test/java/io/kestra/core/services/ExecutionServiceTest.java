package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.ResolvedTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.property.Property;
import io.kestra.plugin.core.debug.Return; 
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@KestraTest
class ExecutionServiceTest {
    @Inject
    ExecutionService executionService;

    @Test
    void resolveTaskRuns() throws Exception {

        Task task = Return.builder()
            .id("task-1")
            .type(Return.class.getName())
            .format(Property.ofValue("test output"))
            .build();

        Flow flow = Flow.builder()
            .id("flow-1")
            .namespace("io.kestra.tests")
            .tasks(List.of(task))
            .build();

        TaskRun taskRun = TaskRun.builder()
            .id("run-1")
            .taskId("task-1")
            .flowId("flow-1")
            .namespace("io.kestra.tests")
            .build();

        List<ResolvedTaskRun> results = executionService.resolveTaskRuns(flow, List.of(taskRun));

        assertEquals(1, results.size());
        assertEquals("run-1", results.get(0).getTaskRun().getId());
        assertEquals("task-1", results.get(0).getResolvedTask().getTask().getId());
        assertNotNull(results.get(0).getResolvedTask().getTask());
    }
}
