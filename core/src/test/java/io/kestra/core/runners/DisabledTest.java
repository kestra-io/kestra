package io.kestra.core.runners;

import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class DisabledTest {
    @Test
    @ExecuteFlow("flows/valids/disable-simple.yaml")
    void simple(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(2);
    }

    @Test
    @ExecuteFlow("flows/valids/disable-error.yaml")
    void error(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(3);
    }

    @Test
    @ExecuteFlow("flows/valids/disable-flowable.yaml")
    void flowable(Execution execution) {
        assertThat(execution.getTaskRunList()).hasSize(10);
    }
}
