package io.kestra.core.repositories;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.ExecutionOutput;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractExecutionOutputRepositoryTest {
    @Inject
    protected ExecutionOutputRepositoryInterface executionOutputRepository;

    @Test
    void shouldSaveAndFindById() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        byte[] value = "test output value".getBytes(StandardCharsets.UTF_8);
        String uri = "kestra://outputs/" + executionId;

        // When
        ExecutionOutput saved = executionOutputRepository.save(new ExecutionOutput(executionId, tenant, value, uri));

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.executionId()).isEqualTo(executionId);

        Optional<ExecutionOutput> found = executionOutputRepository.findById(tenant, executionId);
        assertThat(found).isPresent();
        assertThat(found.get().executionId()).isEqualTo(executionId);
        assertThat(found.get().tenantId()).isEqualTo(tenant);
        assertThat(found.get().value()).isEqualTo(value);
        assertThat(found.get().uri()).isEqualTo(uri);
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());

        // When
        Optional<ExecutionOutput> found = executionOutputRepository.findById(tenant, IdUtils.create());

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldIsolateTenants() {
        // Given
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();

        // When
        executionOutputRepository.save(new ExecutionOutput(executionId, tenant1, "test output".getBytes(StandardCharsets.UTF_8), null));

        // Then
        assertThat(executionOutputRepository.findById(tenant1, executionId)).isPresent();
        assertThat(executionOutputRepository.findById(tenant2, executionId)).isEmpty();
    }

    @Test
    void shouldUpdateExistingExecutionOutput() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        byte[] updated = "updated value".getBytes(StandardCharsets.UTF_8);
        executionOutputRepository.save(new ExecutionOutput(executionId, tenant, "initial value".getBytes(StandardCharsets.UTF_8), null));

        // When
        executionOutputRepository.save(new ExecutionOutput(executionId, tenant, updated, null));

        // Then
        Optional<ExecutionOutput> found = executionOutputRepository.findById(tenant, executionId);
        assertThat(found).isPresent();
        assertThat(found.get().value()).isEqualTo(updated);
    }

    @Test
    protected void shouldPurgeExecutionOutputs() {
        // Given
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId1 = IdUtils.create();
        String executionId2 = IdUtils.create();

        executionOutputRepository.save(new ExecutionOutput(executionId1, tenant, "output 1".getBytes(StandardCharsets.UTF_8), null));
        executionOutputRepository.save(new ExecutionOutput(executionId2, tenant, "output 2".getBytes(StandardCharsets.UTF_8), null));

        // When
        int purged = executionOutputRepository.purgeByExecutionIds(List.of(executionId1, executionId2));

        // Then
        assertThat(purged).isEqualTo(2);
        assertThat(executionOutputRepository.findById(tenant, executionId1)).isEmpty();
        assertThat(executionOutputRepository.findById(tenant, executionId2)).isEmpty();
    }
}
