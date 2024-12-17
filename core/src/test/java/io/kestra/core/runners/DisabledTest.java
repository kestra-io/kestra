package io.kestra.core.runners;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

@KestraTest(startRunner = true)
public class DisabledTest {
    @ExecuteFlow("flows/valids/disable-simple.yaml")
    void simple(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(2));
    }

    @ExecuteFlow("flows/valids/disable-error.yaml")
    void error(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(3));
    }

    @ExecuteFlow("flows/valids/disable-flowable.yaml")
    void flowable(Execution execution) {
        assertThat(execution.getTaskRunList(), hasSize(10));
    }
}
