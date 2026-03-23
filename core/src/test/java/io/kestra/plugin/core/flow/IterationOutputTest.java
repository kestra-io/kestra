package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class IterationOutputTest {

    @Test
    @ExecuteFlow("flows/valids/iteration-output.yaml")
    void iterationOutputPrefixSum(Execution execution){
        var innerSumOutput = (Map<?,?>) execution.outputs().get("inner_even_indices_sum");
        var firstOuterIteration = (Map<?,?>) innerSumOutput.get("100");
        var lastInnerOutput1 = (Map<?,?>) firstOuterIteration.get("14");

        assertThat(lastInnerOutput1.get("value").toString().trim()).isEqualTo("318");

        var secondOuterIteration = (Map<?,?>) innerSumOutput.get("200");
        var lastInnerOutput2 = (Map<?,?>) secondOuterIteration.get("14");

        assertThat(lastInnerOutput2.get("value").toString().trim()).isEqualTo("618");

        var thirdOuterIteration = (Map<?,?>) innerSumOutput.get("300");
        var lastInnerOutput3 = (Map<?,?>) thirdOuterIteration.get("14");

        assertThat(lastInnerOutput3.get("value").toString().trim()).isEqualTo("918");


        var iteration_output_sibling = (Map<?,?>) execution.outputs().get("iteration_output_sibling");
        var firstSiblingOutput = (Map<?,?>) iteration_output_sibling.get("100");
        var lastSiblingOutput1 = (Map<?,?>) firstSiblingOutput.get("14");

        assertThat(lastSiblingOutput1.get("value").toString().trim()).isEqualTo("206");


        var secondSiblingOutput = (Map<?,?>) iteration_output_sibling.get("200");
        var lastSiblingOutput2 = (Map<?,?>) secondSiblingOutput.get("14");

        assertThat(lastSiblingOutput2.get("value").toString().trim()).isEqualTo("406");


        var thirdSiblingOutput = (Map<?,?>) iteration_output_sibling.get("300");
        var lastSiblingOutput3 = (Map<?,?>) thirdSiblingOutput.get("14");

        assertThat(lastSiblingOutput3.get("value").toString().trim()).isEqualTo("606");


        var outerSumOutput = (Map<?,?>) execution.outputs().get("outer_prefix_sum");
        var outerOutput = (Map<?,?>) outerSumOutput.get("300");

        assertThat(outerOutput.get("value").toString().trim()).isEqualTo("600");


        var defaultSumOutput = (Map<?,?>) execution.outputs().get("default_all_prefix_sum");
        var allDefaultOutput = (Map<?,?>) defaultSumOutput.get("300");

        assertThat(allDefaultOutput.get("value").toString().trim()).isEqualTo("1100");


        var iterationDefaultSumOutput = (Map<?,?>) execution.outputs().get("default_iteration_prefix_sum");
        var iterationDefaultOutput = (Map<?,?>) iterationDefaultSumOutput.get("300");

        assertThat(iterationDefaultOutput.get("value").toString().trim()).isEqualTo("600");


        var taskIdDefaultSumOutput = (Map<?,?>) execution.outputs().get("default_task_id_prefix_sum");
        var taskIdDefaultOutput = (Map<?,?>) taskIdDefaultSumOutput.get("300");

        assertThat(taskIdDefaultOutput.get("value").toString().trim()).isEqualTo("600");

    }
}
