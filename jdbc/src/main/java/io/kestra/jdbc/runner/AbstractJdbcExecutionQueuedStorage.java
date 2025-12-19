package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.ExecutionQueued;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

public abstract class AbstractJdbcExecutionQueuedStorage extends AbstractJdbcRepository {
    private static final int MAX_RETRIES = 50;
    private static final int RETRY_BASE_WAIT_MS = 10;
    private static final int MAX_WAIT_MS = 100;
    protected io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository;

    public AbstractJdbcExecutionQueuedStorage(io.kestra.jdbc.AbstractJdbcRepository<ExecutionQueued> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public void save(DSLContext dslContext, ExecutionQueued executionQueued) {
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(executionQueued);
        this.jdbcRepository.persist(executionQueued, dslContext, fields);
    }

    /**
     * Attempts to pop an execution from the queue and process it.
     * This method implements a robust retry mechanism to handle database lock contention,
     * which is common in high-throughput queueing systems.
     * <p>
     * The strategy is as follows:
     * 1. It attempts to fetch and lock one item from the queue using {@code SELECT ... FOR UPDATE SKIP LOCKED}.
     *    Each attempt is performed in its own short-lived transaction to ensure a fresh view of the database
     *    and to avoid holding connections and locks for extended periods.
     * 2. If an item is successfully fetched and processed, the method returns immediately.
     * 3. If no item is fetched, it could be due to either an empty queue or lock contention (i.e., other workers
     *    have locked all available items).
     * 4. To differentiate, it performs a quick {@code COUNT} query. If the queue is empty, it exits,
     *    avoiding unnecessary retries.
     * 5. If the queue is not empty, it waits for a short period using a capped exponential backoff strategy
     *    before retrying. This prevents busy-waiting and reduces load on the database.
     *
     * @param tenantId  The tenant ID of the execution.
     * @param namespace The namespace of the flow.
     * @param flowId    The flow ID.
     * @param consumer  The consumer to process the execution if one is successfully popped.
     */
    public void pop(String tenantId, String namespace, String flowId, BiConsumer<DSLContext, Execution> consumer) {
        // Retry loop to handle potential lock contention.
        for (int retryCount = 0; retryCount < MAX_RETRIES; retryCount++) {
            // Each attempt is a new, short-lived transaction to get a fresh database view and avoid holding locks.
            boolean processed = this.jdbcRepository
                .getDslContextWrapper()
                .transactionResult(configuration -> {
                    var dslContext = DSL.using(configuration);

                    var select = dslContext
                        .select(AbstractJdbcRepository.field("value"))
                        .from(this.jdbcRepository.getTable())
                        .where(buildTenantCondition(tenantId))
                        .and(field("namespace").eq(namespace))
                        .and(field("flow_id").eq(flowId))
                        .orderBy(field("date").asc())
                        .limit(1)
                        .forUpdate()
                        .skipLocked();

                    Optional<ExecutionQueued> maybeExecution = this.jdbcRepository.fetchOne(select);

                    if (maybeExecution.isPresent()) {
                        consumer.accept(dslContext, maybeExecution.get().getExecution());
                        this.jdbcRepository.delete(maybeExecution.get());
                        return true;
                    }

                    return false;
                });

            if (processed) {
                // Successfully processed an item, no need to retry.
                return;
            }

            // If not processed, check if the queue is actually empty to avoid useless retries.
            int count = this.jdbcRepository.getDslContextWrapper().transactionResult(configuration -> DSL.using(configuration)
                .selectCount()
                .from(this.jdbcRepository.getTable())
                .where(buildTenantCondition(tenantId))
                .and(field("namespace").eq(namespace))
                .and(field("flow_id").eq(flowId))
                .fetchOne(0, int.class)
            );

            if (count == 0) {
                // Queue is empty, no need for further retries.
                return;
            }

            // Queue is not empty, but we couldn't get a lock. Wait before the next retry.
            if (retryCount < MAX_RETRIES - 1) {
                try {
                    // Use capped exponential backoff to prevent excessive waiting times.
                    long waitTime = Math.min((long) RETRY_BASE_WAIT_MS * (retryCount + 1), MAX_WAIT_MS);
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * This method should only be used for administration purpose via a command
     */
    public List<ExecutionQueued> getAllForAllTenants() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select(AbstractJdbcRepository.field("value"))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    public void remove(Execution execution) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                DSL
                .using(configuration)
                .deleteFrom(this.jdbcRepository.getTable())
                .where(buildTenantCondition(execution.getTenantId()))
                .and(field("key").eq(IdUtils.fromParts(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), execution.getId())))
                .execute();
            });
    }
}
