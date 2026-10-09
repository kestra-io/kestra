package io.kestra.executor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Concurrency;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.runners.ExecutionQueuedStateStore;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavior every {@link ExecutionQueuedStateStore} backend must honor, extended once per backend. Queued executions
 * are saved and popped inside the concurrency-limit transaction of the same backend, as the executor does.
 */
@MicronautTest(transactional = false)
public abstract class AbstractExecutionQueuedStateStoreTest {

    @Inject
    protected ExecutionQueuedStateStore executionQueuedStateStore;

    @Inject
    protected ConcurrencyLimitStateStore concurrencyLimitStateStore;

    @Test
    void shouldPopAtMostOneExecutionPerCall() {
        Flow flow = flow("main");
        List<Execution> executions = queue(flow, 3);

        try {
            AtomicInteger consumerInvocations = new AtomicInteger();
            List<Execution> popped = new ArrayList<>();
            pop(flow, exec ->
            {
                consumerInvocations.incrementAndGet();
                popped.add(exec);
            });

            assertThat(consumerInvocations.get()).isEqualTo(1);
            assertThat(popped).hasSize(1);
            assertThat(popped.getFirst().getId()).isEqualTo(executions.getFirst().getId());

            popped.clear();
            consumerInvocations.set(0);
            pop(flow, exec ->
            {
                consumerInvocations.incrementAndGet();
                popped.add(exec);
            });
            assertThat(consumerInvocations.get()).isEqualTo(1);
            assertThat(popped.getFirst().getId()).isEqualTo(executions.get(1).getId());
        } finally {
            executions.forEach(executionQueuedStateStore::remove);
        }
    }

    @Test
    void shouldReturnQueuedExecutionsOfEveryTenantWhenGettingAllForAllTenants() {
        Flow mainFlow = flow("main");
        Flow otherFlow = flow("other");
        List<Execution> mainExecutions = queue(mainFlow, 2);
        List<Execution> otherExecutions = queue(otherFlow, 1);

        try {
            List<String> all = executionQueuedStateStore.getAllForAllTenants().stream().map(queued -> queued.getExecution().getId()).toList();

            assertThat(all).containsSubsequence(mainExecutions.stream().map(Execution::getId).toList());
            assertThat(all).containsAll(otherExecutions.stream().map(Execution::getId).toList());
        } finally {
            mainExecutions.forEach(executionQueuedStateStore::remove);
            otherExecutions.forEach(executionQueuedStateStore::remove);
        }
    }

    private void pop(Flow flow, Consumer<Execution> consumer) {
        concurrencyLimitStateStore.countThenProcess(flow, (txContext, limit) ->
        {
            executionQueuedStateStore.pop(txContext, flow.getTenantId(), flow.getNamespace(), flow.getId(), (ctx, execution) -> consumer.accept(execution));
            return Pair.of(null, limit);
        });
    }

    private List<Execution> queue(Flow flow, int count) {
        Instant now = Instant.now();
        List<Execution> executions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            executions.add(Execution.newExecution(flow, Collections.emptyList()));
        }
        concurrencyLimitStateStore.countThenProcess(flow, (txContext, limit) ->
        {
            for (int i = 0; i < count; i++) {
                executionQueuedStateStore.save(
                    txContext,
                    ExecutionQueued.builder()
                        .tenantId(flow.getTenantId())
                        .namespace(flow.getNamespace())
                        .flowId(flow.getId())
                        .execution(executions.get(i))
                        .date(now.plusSeconds(i))
                        .build()
                );
            }
            return Pair.of(null, limit);
        });
        return executions;
    }

    private static Flow flow(String tenantId) {
        return Flow.builder()
            .tenantId(tenantId)
            .namespace("io.kestra.unittest")
            .id(IdUtils.create())
            .revision(1)
            .concurrency(Concurrency.builder().behavior(Concurrency.Behavior.QUEUE).limit(1).build())
            .tasks(List.of(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();
    }
}
