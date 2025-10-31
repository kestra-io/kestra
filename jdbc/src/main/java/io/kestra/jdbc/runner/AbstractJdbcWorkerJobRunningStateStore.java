package io.kestra.jdbc.runner;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerJobRunningStateStore;
import io.kestra.jdbc.repository.AbstractJdbcRepository;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.function.BiConsumer;

@Slf4j
public abstract class AbstractJdbcWorkerJobRunningStateStore extends AbstractJdbcRepository implements WorkerJobRunningStateStore {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository;

    public AbstractJdbcWorkerJobRunningStateStore(io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public WorkerJobRunning save(TransactionContext txContext, WorkerJobRunning workerJobRunning) {
        // if both queue and repository support the same transaction type, we participate in the transaction, otherwise, not
        if (txContext.supports(JdbcTransactionContext.class)) {
            var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
            this.jdbcRepository.persist(workerJobRunning, dslContext, this.jdbcRepository.persistFields(workerJobRunning));
        } else {
            this.jdbcRepository.persist(workerJobRunning);
        }
        return workerJobRunning;
    }

    @Override
    public void deleteByKey(TransactionContext txContext, String key) {
        // if both queue and repository support the same transaction type, we participate in the transaction, otherwise, not
        if (txContext.supports(JdbcTransactionContext.class)) {
            var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
            deleteByKey(dslContext, key);
        } else {
            this.jdbcRepository.getDslContextWrapper().transaction(configuration -> {
                var dslContext = DSL.using(configuration);
                deleteByKey(dslContext, key);
            });
        }
    }

    private void deleteByKey(DSLContext dslContext, String key) {
        dslContext
            .transaction(configuration ->
                DSL
                    .using(configuration)
                    .deleteFrom(this.jdbcRepository.getTable())
                    .where(field("key").eq(key))
                    .execute()
            );
    }

    @VisibleForTesting
    public List<WorkerJobRunning> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                var select = DSL
                    .using(configuration)
                    .select((field("value")))
                    .from(this.jdbcRepository.getTable());

                return this.jdbcRepository.fetch(select);
            });
    }

    @Override
    public void deleteByKey(String key) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration ->
                DSL
                    .using(configuration)
                    .deleteFrom(this.jdbcRepository.getTable())
                    .where(field("key").eq(key))
                    .execute()
            );
    }

    @Override
    public void processWorkerJobsForDeadWorker(TransactionContext txContext, String workerUid, BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        // if both queue and repository support the same transaction type, we participate in the transaction, otherwise, not
        if (txContext.supports(JdbcTransactionContext.class)) {
            var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
            dslContext
                .select(field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("worker_uuid").eq(workerUid))
                .forUpdate()
                .fetch()
                .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)))
                .forEach(it -> consumer.accept(txContext, it));
        } else {
            this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> {
                    DSL.using(configuration)
                        .select(field("value"))
                        .from(this.jdbcRepository.getTable())
                        .where(field("worker_uuid").eq(workerUid))
                        .forUpdate()
                        .fetch()
                        .map(r -> this.jdbcRepository.deserialize(r.get("value", String.class)))
                        .forEach(it -> consumer.accept(txContext, it));
                });
        }
    }
}

