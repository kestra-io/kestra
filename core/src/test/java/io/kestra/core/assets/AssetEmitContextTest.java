package io.kestra.core.assets;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.runners.WorkerTask;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

class AssetEmitContextTest {

    @Test
    void shouldIdentifyTheTaskRunAndTheFlowRevisionWhenTheVariablesHoldIt() {
        AssetEmitContext context = AssetEmitContext.of(workerTask(ExecutionKind.TEST), Map.of("flow", Map.of("revision", 3)));

        assertThat(context).isEqualTo(
            new AssetEmitContext("tenant", "io.kestra.unit-test", "flow", 3, "execution", "log", "task-run", Log.class.getName(), ExecutionKind.TEST)
        );
    }

    @Test
    void shouldLeaveTheFlowRevisionNullWhenTheVariablesHaveNone() {
        AssetEmitContext context = AssetEmitContext.of(workerTask(null), Map.of("flow", Map.of("id", "flow")));

        assertThat(context.flowRevision()).isNull();
        assertThat(context.executionKind()).isNull();
    }

    private static WorkerTask workerTask(ExecutionKind executionKind) {
        Log task = Log.builder().id("log").type(Log.class.getName()).message("hello").build();
        TaskRun taskRun = TaskRun.builder()
            .tenantId("tenant")
            .namespace("io.kestra.unit-test")
            .flowId("flow")
            .executionId("execution")
            .taskId("log")
            .id("task-run")
            .build();
        WorkerTask workerTask = WorkerTask.builder().task(task).taskRun(taskRun).build();
        workerTask.setExecutionKind(executionKind);
        return workerTask;
    }
}
