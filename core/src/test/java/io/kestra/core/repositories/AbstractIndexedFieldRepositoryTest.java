package io.kestra.core.repositories;

import java.util.*;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest(transactional = false)
public abstract class AbstractIndexedFieldRepositoryTest {
    @Inject
    protected IndexedFieldRepositoryInterface indexedFieldRepository;

    private Execution createExecution(String tenant, String executionId) {
        return Execution.builder()
            .id(executionId)
            .tenantId(tenant)
            .namespace("io.kestra.unittest")
            .flowId("test-flow")
            .flowRevision(1)
            .state(new State())
            .build();
    }

    @Test
    void should_save_and_find_by_execution() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        Execution execution = createExecution(tenant, executionId);

        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId, "customer", "acme", "io.kestra.unittest", "test-flow"));
        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId, "order", "12345", "io.kestra.unittest", "test-flow"));

        List<ExecutionIndexedField> fields = indexedFieldRepository.findByExecution(execution);
        assertThat(fields).hasSize(2);
        assertThat(fields)
            .extracting(ExecutionIndexedField::key)
            .containsExactlyInAnyOrder("customer", "order");
        assertThat(fields)
            .extracting(ExecutionIndexedField::value)
            .containsExactlyInAnyOrder("acme", "12345");
    }

    @Test
    void should_find_execution_ids_by_exact_value() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId, "customer", "acme", "io.kestra.unittest", "test-flow"));

        List<String> exact = indexedFieldRepository.findExecutionIds(tenant, "customer", "acme", true);
        assertThat(exact).containsExactly(executionId);

        List<String> noMatch = indexedFieldRepository.findExecutionIds(tenant, "customer", "other", true);
        assertThat(noMatch).isEmpty();
    }

    @Test
    void should_find_execution_ids_by_substring_value() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId, "customer", "acme-corp", "io.kestra.unittest", "test-flow"));

        List<String> matches = indexedFieldRepository.findExecutionIds(tenant, "customer", "corp", false);
        assertThat(matches).containsExactly(executionId);
    }

    @Test
    void should_isolate_tenants() {
        String tenant1 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String tenant2 = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId = IdUtils.create();
        indexedFieldRepository.save(new ExecutionIndexedField(tenant1, executionId, "customer", "acme", "io.kestra.unittest", "test-flow"));

        assertThat(indexedFieldRepository.findExecutionIds(tenant1, "customer", "acme", true)).containsExactly(executionId);
        assertThat(indexedFieldRepository.findExecutionIds(tenant2, "customer", "acme", true)).isEmpty();
    }

    @Test
    void should_purge_by_execution_ids() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        String executionId1 = IdUtils.create();
        String executionId2 = IdUtils.create();
        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId1, "customer", "acme", "io.kestra.unittest", "test-flow"));
        indexedFieldRepository.save(new ExecutionIndexedField(tenant, executionId2, "customer", "other", "io.kestra.unittest", "test-flow"));

        int purged = indexedFieldRepository.purgeByExecutionIds(List.of(executionId1, executionId2));
        assertThat(purged).isEqualTo(2);
        assertThat(indexedFieldRepository.findByExecution(createExecution(tenant, executionId1))).isEmpty();
    }
}
