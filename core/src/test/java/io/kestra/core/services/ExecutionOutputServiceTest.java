package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionOutputRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ExecutionOutputServiceTest {
    @Inject
    private ExecutionOutputService executionOutputService;

    @Inject
    private ExecutionOutputRepositoryInterface executionOutputRepository;

    private Execution execution(String tenant, State.Type state) {
        return Execution.builder()
            .id(IdUtils.create())
            .tenantId(tenant)
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State(state))
            .build();
    }

    @Test
    void shouldSaveAndGetOutputs() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        Map<String, Object> outputs = Map.of(
            "key1", "value1",
            "key2", 42,
            "key3", Map.of("nested", "value"),
            "key4", List.of(1, 2, 3)
        );

        // When
        executionOutputService.saveOutputs(execution, outputs);

        // Then
        Map<String, Object> retrieved = executionOutputService.getOutputs(execution);
        assertThat(retrieved).hasSize(4);
        assertThat(retrieved.get("key1")).isEqualTo("value1");
        assertThat(retrieved.get("key2")).isEqualTo(42);
        assertThat(retrieved.get("key3")).isInstanceOf(Map.class);
        assertThat((List<?>) retrieved.get("key4")).hasSize(3);

        var saved = executionOutputRepository.findById(tenant, execution.getId());
        assertThat(saved).isPresent();
        assertThat(saved.get().value()).isNotNull();
        assertThat(saved.get().uri()).isNull();
    }

    @Test
    void shouldNotSaveWhenOutputsAreEmpty() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);

        // When
        executionOutputService.saveOutputs(execution, Collections.emptyMap());

        // Then
        assertThat(executionOutputRepository.findById(tenant, execution.getId())).isEmpty();
        assertThat(executionOutputService.getOutputs(execution)).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenNotFound() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Map<String, Object> outputs = executionOutputService.getOutputs(execution(tenant, State.Type.SUCCESS));

        // Then
        assertThat(outputs).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenExecutionIsNotTerminated() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution = execution(tenant, State.Type.SUCCESS);
        executionOutputService.saveOutputs(execution, Map.of("key", "value"));

        // When
        Map<String, Object> outputs = executionOutputService.getOutputs(execution.withState(State.Type.RUNNING));

        // Then
        assertThat(outputs).isEmpty();
    }

    @SuppressWarnings("deprecation")
    @Test
    void shouldReturnDeprecatedOutputsFieldWhenSet() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Map<String, Object> legacyOutputs = Map.of("legacyKey", "legacyValue", "count", 42);
        Execution execution = execution(tenant, State.Type.SUCCESS).withOutputs(legacyOutputs);

        // When - nothing saved to the repository, the pre-2.0 field must be used
        Map<String, Object> outputs = executionOutputService.getOutputs(execution);

        // Then
        assertThat(outputs).containsExactlyInAnyOrderEntriesOf(legacyOutputs);
    }

    @Test
    void shouldPurgeOnlyTheGivenExecutions() throws InternalException {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        Execution execution1 = execution(tenant, State.Type.SUCCESS);
        Execution execution2 = execution(tenant, State.Type.SUCCESS);
        executionOutputService.saveOutputs(execution1, Map.of("key", "value1"));
        executionOutputService.saveOutputs(execution2, Map.of("key", "value2"));

        // When
        int purged = executionOutputService.purge(List.of(execution1));

        // Then
        assertThat(purged).isEqualTo(1);
        assertThat(executionOutputRepository.findById(tenant, execution1.getId())).isEmpty();
        assertThat(executionOutputRepository.findById(tenant, execution2.getId())).isPresent();
    }
}
