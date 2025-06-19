package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ForEachItemTest {

    @Test
    public void testSubflowCreation() throws Exception {
        List<Map<String, Object>> items = List.of(
            Map.of("namespace", "team.ns1", "flowId", "flow-1", "input1", "A"),
            Map.of("namespace", "team.ns2", "flowId", "flow-2", "input1", "B")
        );

        java.io.File tempFile = java.io.File.createTempFile("items", ".json");
        new ObjectMapper().writeValue(tempFile, items);

       ForEachItem task = ForEachItem.builder()
    .id("loop")
    .type(ForEachItem.class.getName())
    .items("kestra://path/to/items/file")
    .batch(ForEachItem.Batch.builder().rows(Property.ofValue(1)).build())
    .namespace("{{ ( read(taskrun.items) ) | jq('.namespace') | first }}")
    .flowId("{{ ( read(taskrun.items) ) | jq('.flowId') | first }}")
    .inputs(Map.of("input1", "{{ ( read(taskrun.items) ) | jq('.input1') | first }}"))
    .build();
    
        RunContext runContext = TestsUtils.mockRunContext(null, task, null);
        FlowMetaStoreInterface metaStore = mock(FlowMetaStoreInterface.class);
        Execution execution = mock(Execution.class);
        TaskRun taskRun = mock(TaskRun.class);

        when(taskRun.getId()).thenReturn("test-taskrun");
        when(taskRun.attemptNumber()).thenReturn(1);
        when(taskRun.getOutputs()).thenReturn(io.kestra.core.models.tasks.common.VariableUtils.variables(Map.of()));

        Flow currentFlow = mock(Flow.class);

        when(runContext.storage().getFile(any())).thenReturn(new java.io.FileInputStream(tempFile));

        List<SubflowExecution<?>> subflows = task.createSubflowExecutions(runContext, metaStore, currentFlow, execution, taskRun);

        assertEquals(2, subflows.size());
        assertEquals("flow-1", subflows.get(0).getFlowId());
        assertEquals("flow-2", subflows.get(1).getFlowId());
        assertEquals("team.ns1", subflows.get(0).getNamespace());
        assertEquals("team.ns2", subflows.get(1).getNamespace());
    }
}
// Java
@Override
public List<SubflowExecution<?>> createSubflowExecutions(
    RunContext runContext,
    FlowMetaStoreInterface metaStore,
    Flow currentFlow,
    Execution execution,
    TaskRun taskRun
) {
    // Find the ForEachItemExecutable child task
    return this.getTasks().stream()
        .filter(t -> t instanceof ForEachItemExecutable)
        .map(t -> (ForEachItemExecutable) t)
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("ForEachItemExecutable child task not found"))
        .createSubflowExecutions(runContext, metaStore, currentFlow, execution, taskRun);
}