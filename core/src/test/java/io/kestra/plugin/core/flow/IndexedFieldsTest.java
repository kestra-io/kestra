package io.kestra.plugin.core.flow;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.repositories.IndexedFieldRepositoryInterface;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(startRunner = true)
class IndexedFieldsTest {
    @Inject
    private IndexedFieldRepositoryInterface indexedFieldRepository;

    @Test
    @ExecuteFlow("flows/valids/indexed-fields.yaml")
    void shouldStoreIndexedFieldsWhenExecutionEnds(Execution execution) {
        List<ExecutionIndexedField> fields = indexedFieldRepository.findByExecution(execution);
        assertThat(fields).hasSize(2);
        assertThat(fields)
            .extracting(ExecutionIndexedField::key)
            .containsExactlyInAnyOrder("customerId", "region");
        assertThat(fields)
            .filteredOn(field -> field.key().equals("customerId"))
            .extracting(ExecutionIndexedField::value)
            .containsExactly("acme-corp");
        assertThat(fields)
            .filteredOn(field -> field.key().equals("region"))
            .extracting(ExecutionIndexedField::value)
            .containsExactly("europe");
    }

    @Test
    @ExecuteFlow("flows/valids/indexed-fields.yaml")
    void shouldBeSearchableByIndexedField(Execution execution) {
        List<String> exact = indexedFieldRepository.findExecutionIds(execution.getTenantId(), "customerId", "acme-corp", true);
        assertThat(exact).contains(execution.getId());

        List<String> substring = indexedFieldRepository.findExecutionIds(execution.getTenantId(), "customerId", "acme", false);
        assertThat(substring).contains(execution.getId());
    }
}
