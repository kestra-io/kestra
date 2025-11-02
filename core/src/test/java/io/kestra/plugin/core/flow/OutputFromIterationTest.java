package io.kestra.plugin.core.flow;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class OutputFromIterationTest {

    @Test
    @ExecuteFlow("flows/valids/previous-output.yaml")
    void outputFromIterationPrefixSum(Execution execution)
    {

          var sumOutput1= (Map<?,?>) execution.outputs().get("even_indices_prefix_sum");
          var lastOddIterationOutput=(Map<?,?>) sumOutput1.get("14");
          var lastEvenIterationOutput=(Map<?,?>) sumOutput1.get("12");

          assertThat(lastEvenIterationOutput.get("value").toString().trim()).isEqualTo("18");
          assertThat(lastOddIterationOutput.get("value").toString().trim()).isEqualTo("18");

          var sumOutput2= (Map<?,?>) execution.outputs().get("prefix_sum");
          var lastOutput=(Map<?,?>) sumOutput2.get("14");
          assertThat(lastOutput.get("value").toString().trim()).isEqualTo("45");
    }
}
