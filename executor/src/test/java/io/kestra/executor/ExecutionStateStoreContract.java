package io.kestra.executor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral contract every {@link ExecutionStateStore} implementation must honor —
 * annotation-free so it can run against both container-injected stores (JDBC/Elasticsearch
 * through {@link AbstractExecutionStateStoreTest}) and hand-built ones (the executor testkit's
 * in-memory fake), keeping fakes provably faithful to the production implementations.
 * Subclasses provide the store under test via {@link #store()}.
 */
public abstract class ExecutionStateStoreContract {

    /** The {@link ExecutionStateStore} implementation under contract. */
    protected abstract ExecutionStateStore store();

    protected static Flow flow() {
        return Flow.builder()
            .tenantId(null)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
    }

    @Test
    void shouldCreateAndFindExecution() {
        // Given
        Flow flow = flow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList());

        // When
        Execution created = store().create(execution);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo(execution.getId());

        Execution found = store().findById(execution.getId());
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(execution.getId());
        assertThat(found.getNamespace()).isEqualTo(flow.getNamespace());
        assertThat(found.getFlowId()).isEqualTo(flow.getId());
    }

    @Test
    void shouldReturnNullWhenExecutionNotFound() {
        // When
        Execution found = store().findById(IdUtils.create());

        // Then
        assertThat(found).isNull();
    }

    @Test
    void shouldReturnEmptyWhenLockingNonExistentExecution() {
        // When
        Optional<ExecutorContext> result = store().lock(IdUtils.create(), ExecutorContext::new);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldLockAndPersistExecutionWhenExecutionExists() {
        // Given
        Flow flow = flow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList());
        store().create(execution);

        // When
        Execution updated = execution.withState(State.Type.SUCCESS);
        Optional<ExecutorContext> result = store().lock(
            execution.getId(),
            e -> new ExecutorContext(updated)
        );

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getExecution().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution persisted = store().findById(execution.getId());
        assertThat(persisted.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    void shouldReturnEmptyWhenLockFunctionReturnsNull() {
        // Given
        Flow flow = flow();
        Execution execution = Execution.newExecution(flow, Collections.emptyList());
        store().create(execution);

        // When — returning null signals no-op: nothing is persisted
        Optional<ExecutorContext> result = store().lock(execution.getId(), e -> null);

        // Then
        assertThat(result).isEmpty();
    }
}
