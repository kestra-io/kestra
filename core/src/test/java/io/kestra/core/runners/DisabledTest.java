package io.kestra.core.runners;

import io.kestra.core.repositories.ExecutionRepositoryInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
public class DisabledTest {
    @Inject
    private ExecutionRepositoryInterface executionRepository;
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
        assertThat(execution.getTaskRunList()).hasSize(7);

        var subExecutions = executionRepository.findLoopSubExecutions(execution.getTenantId(), execution.getId());
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions.stream().map(Execution::getTaskRunList).mapToLong(List::size).sum()).isEqualTo(3);
    }
}
