package io.kestra.plugin.core.flow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.services.TaskOutputService;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class IterationOutputTest {

    @Inject
    private TaskOutputService taskOutputService;

    @Test
    @ExecuteFlow("flows/valids/iteration-output.yaml")
    void iterationOutputPrefixSum(Execution execution) throws InternalException {
        var lastInnerOutput1 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("inner_even_indices_sum", List.of("100", "14")));
        assertThat(lastInnerOutput1.get("value").toString().trim()).isEqualTo("318");

        var lastInnerOutput2 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("inner_even_indices_sum", List.of("200", "14")));
        assertThat(lastInnerOutput2.get("value").toString().trim()).isEqualTo("618");

        var lastInnerOutput3 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("inner_even_indices_sum", List.of("300", "14")));
        assertThat(lastInnerOutput3.get("value").toString().trim()).isEqualTo("918");

        var lastSiblingOutput1 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("iteration_output_sibling", List.of("100", "14")));
        assertThat(lastSiblingOutput1.get("value").toString().trim()).isEqualTo("206");

        var lastSiblingOutput2 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("iteration_output_sibling", List.of("200", "14")));
        assertThat(lastSiblingOutput2.get("value").toString().trim()).isEqualTo("406");

        var lastSiblingOutput3 = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("iteration_output_sibling", List.of("300", "14")));
        assertThat(lastSiblingOutput3.get("value").toString().trim()).isEqualTo("606");

        var outerOutput = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("outer_prefix_sum", List.of("300")));
        assertThat(outerOutput.get("value").toString().trim()).isEqualTo("600");

        var allDefaultOutput = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("default_all_prefix_sum", List.of("300")));
        assertThat(allDefaultOutput.get("value").toString().trim()).isEqualTo("1100");

        var iterationDefaultOutput = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("default_iteration_prefix_sum", List.of("300")));
        assertThat(iterationDefaultOutput.get("value").toString().trim()).isEqualTo("600");

        var taskIdDefaultOutput = taskOutputService.getOutputs(execution.findTaskRunByTaskIdAndValue("default_task_id_prefix_sum", List.of("300")));
        assertThat(taskIdDefaultOutput.get("value").toString().trim()).isEqualTo("600");
    }
}
