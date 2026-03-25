package io.kestra.plugin.core.flow;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class ParentOutputFunctionTest {
    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @ExecuteFlow(value = "flows/valids/parent-output.yaml")
    void parentOutput(Execution execution) throws InternalException {
        var taskRun = execution.findTaskRunsByTaskId("parentOutput").getLast();
        var taskOutput = taskOutputService.getOutputs(taskRun);
        assertThat(((Map<?, ?>) taskOutput.get("values")).get("parentOutput")).isEqualTo("{\"evaluationResult\":true}");
    }

    @Test
    @ExecuteFlow(value = "flows/valids/parents-output.yaml")
    void parentsOutput(Execution execution) throws InternalException {
        var taskRun = execution.findTaskRunsByTaskId("parentOutput").getLast();
        var taskOutput = taskOutputService.getOutputs(taskRun);
        assertThat(((Map<?, ?>) taskOutput.get("values")).get("parentOutput")).isEqualTo("{\"evaluationResult\":true}");
    }
}