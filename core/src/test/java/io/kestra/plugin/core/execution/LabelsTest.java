package io.kestra.plugin.core.execution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LabelsTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldIgnoreNullLabelValueWhenLabelsAreAMap() throws Exception {
        Map<String, String> labels = new HashMap<>();
        labels.put("customer", null);
        labels.put("env", "prod");

        Labels task = Labels.builder()
            .id(IdUtils.create())
            .type(Labels.class.getName())
            .labels(labels)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, Map.of());
        Execution execution = Execution.builder().id(IdUtils.create()).build();

        Execution updated = task.update(execution, runContext);

        assertThat(updated.getLabels()).containsExactly(new Label("env", "prod"));
    }

    @Test
    void shouldIgnoreNullLabelValueWhenLabelsResolveToAJsonString() throws Exception {
        Map<String, Object> values = new HashMap<>();
        values.put("billing", "Payments");
        values.put("other", null);
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("values", values);

        Flow flow = TestsUtils.mockFlow();
        String executionId = IdUtils.create();
        TaskRun outputTaskRun = TaskRun.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .executionId(executionId)
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId("out")
            .state(new State())
            .outputs(outputs)
            .build();
        Execution execution = Execution.builder()
            .id(executionId)
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .state(new State())
            .taskRunList(List.of(outputTaskRun))
            .build();

        Labels task = Labels.builder()
            .id(IdUtils.create())
            .type(Labels.class.getName())
            .labels("{{ outputs['out']['values'] }}")
            .build();

        RunContext runContext = runContextFactory.of(flow, task, execution, TestsUtils.mockTaskRun(execution, task));

        Execution updated = task.update(execution, runContext);

        assertThat(updated.getLabels()).containsExactly(new Label("billing", "Payments"));
    }
}
