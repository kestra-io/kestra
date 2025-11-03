package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class OutputFromIterationTest {

    @Test
    @ExecuteFlow("flows/valids/previous-output.yaml")
    void outputFromIterationPrefixSum(Execution execution){
        var innerSumOutput= (Map<?,?>) execution.outputs().get("inner_even_indices_sum");
        var firstOuterIteration= (Map<?,?>) innerSumOutput.get("100");
        var lastInnerOutput1= (Map<?,?>) firstOuterIteration.get("14");

        assertThat(lastInnerOutput1.get("value").toString().trim()).isEqualTo("318");

        var secondOuterIteration= (Map<?,?>) innerSumOutput.get("200");
        var lastInnerOutput2= (Map<?,?>) secondOuterIteration.get("14");

        assertThat(lastInnerOutput2.get("value").toString().trim()).isEqualTo("618");

        var thirdOuterIteration= (Map<?,?>) innerSumOutput.get("300");
        var lastInnerOutput3= (Map<?,?>) thirdOuterIteration.get("14");

        assertThat(lastInnerOutput3.get("value").toString().trim()).isEqualTo("918");

        var outerSumOutput= (Map<?,?>) execution.outputs().get("outer_prefix_sum");
        var outerOutput= (Map<?,?>) outerSumOutput.get("300");

        assertThat(outerOutput.get("value").toString().trim()).isEqualTo("600");
    }
}
