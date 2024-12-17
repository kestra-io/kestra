package io.kestra.plugin.core.flow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

@KestraTest(startRunner = true)
class ParallelTest {

    @ExecuteFlow("flows/valids/parallel.yaml")
    void parallel(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(8));
    }

    @ExecuteFlow("flows/valids/parallel-nested.yaml")
    void parallelNested(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(11));
    }
}
