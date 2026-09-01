package io.kestra.core.utils;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.serializers.YamlParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class GraphUtilsTest {

    @Test
    void shouldKeepTaskNodeWhenTaskRunMovedIntoAFlowable() throws Exception {
        Flow flow = YamlParser.parse(
            """
                id: replay-moved-task
                namespace: io.kestra.tests
                tasks:
                  - id: seq
                    type: io.kestra.plugin.core.flow.Sequential
                    tasks:
                      - id: standalone
                        type: io.kestra.plugin.core.log.Log
                        message: hello
                      - id: boom
                        type: io.kestra.plugin.core.log.Log
                        message: hello
                """,
            Flow.class
        );
        String executionId = IdUtils.create();
        TaskRun seq = taskRun(executionId, flow, "seq", null);
        TaskRun standalone = taskRun(executionId, flow, "standalone", null);
        TaskRun boom = taskRun(executionId, flow, "boom", seq.getId());

        Execution execution = Execution.newExecution(flow, List.of())
            .toBuilder()
            .id(executionId)
            .taskRunList(List.of(seq, standalone, boom))
            .build();

        GraphCluster graph = GraphUtils.of(flow, execution);

        assertThat(GraphUtils.hasTaskRun(graph, boom.getId())).isTrue();
        assertThat(GraphUtils.hasTaskRun(graph, standalone.getId())).isFalse();
    }

    @Test
    void shouldNotHangWhenADagTaskRunBelongsToAnotherParent() throws Exception {
        Flow flow = YamlParser.parse(
            """
                id: replay-moved-dag-task
                namespace: io.kestra.tests
                tasks:
                  - id: d
                    type: io.kestra.plugin.core.flow.Dag
                    tasks:
                      - task:
                          id: a
                          type: io.kestra.plugin.core.log.Log
                          message: hello
                      - task:
                          id: x
                          type: io.kestra.plugin.core.log.Log
                          message: hello
                        dependsOn: [a]
                """,
            Flow.class
        );
        String executionId = IdUtils.create();
        TaskRun d = taskRun(executionId, flow, "d", null);
        TaskRun a = taskRun(executionId, flow, "a", d.getId());
        TaskRun x = taskRun(executionId, flow, "x", null);

        Execution execution = Execution.newExecution(flow, List.of())
            .toBuilder()
            .id(executionId)
            .taskRunList(List.of(d, a, x))
            .build();

        assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () -> GraphUtils.of(flow, execution)
        );
    }

    private TaskRun taskRun(String executionId, Flow flow, String taskId, String parentTaskRunId) {
        return TaskRun.builder()
            .id(IdUtils.create())
            .executionId(executionId)
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(taskId)
            .parentTaskRunId(parentTaskRunId)
            .state(new State())
            .build();
    }
}
