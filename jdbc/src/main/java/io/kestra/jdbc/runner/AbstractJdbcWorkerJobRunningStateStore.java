package io.kestra.jdbc.runner;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.runners.TransactionContext;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.jdbc.repository.AbstractJdbcRepository;

import lombok.extern.slf4j.Slf4j;

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
            // Commit on a dedicated connection: the entry must be visible to concurrent result
            // processing as soon as the job is sent to the worker, even when the caller holds an
            // open thread-bound transaction (the dispatch-queue poll transaction) — otherwise the
            // terminal result of a fast task deletes nothing and the entry leaks forever.
            this.jdbcRepository.getDslContextWrapper().requireNewTransaction(
                configuration -> this.jdbcRepository.persist(workerJobRunning, DSL.using(configuration), this.jdbcRepository.persistFields(workerJobRunning))
            );
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
            this.jdbcRepository.getDslContextWrapper().transaction(configuration ->
            {
                var dslContext = DSL.using(configuration);
                deleteByKey(dslContext, key);
            });
        }
    }

    private void deleteByKey(DSLContext dslContext, String key) {
        dslContext
            .transaction(
                configuration -> DSL
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
            .transactionResult(configuration ->
            {
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
            .transaction(
                configuration -> DSL
                    .using(configuration)
                    .deleteFrom(this.jdbcRepository.getTable())
                    .where(field("key").eq(key))
                    .execute()
            );
    }

    @Override
    public void deleteByKeyAndWorker(TransactionContext txContext, String key, String workerUid) {
        // if both queue and repository support the same transaction type, we participate in the transaction, otherwise, not
        if (txContext.supports(JdbcTransactionContext.class)) {
            deleteByKeyAndWorker(txContext.unwrap(JdbcTransactionContext.class).getDslContext(), key, workerUid);
        } else {
            this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> deleteByKeyAndWorker(DSL.using(configuration), key, workerUid));
        }
    }

    private void deleteByKeyAndWorker(DSLContext dslContext, String key, String workerUid) {
        dslContext
            .transaction(
                configuration -> DSL
                    .using(configuration)
                    .deleteFrom(this.jdbcRepository.getTable())
                    .where(field("key").eq(key))
                    .and(field("worker_uid").eq(workerUid))
                    .execute()
            );
    }

    @Override
    public Set<String> findWorkerUidsWithRunningJobs() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(
                configuration -> DSL
                    .using(configuration)
                    .selectDistinct(field("worker_uid"))
                    .from(this.jdbcRepository.getTable())
                    .fetchSet(field("worker_uid"), String.class)
            );
    }

    @Override
    public void processOrphanWorkerJobs(TransactionContext txContext, Set<String> inactiveWorkerUids, BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        if (inactiveWorkerUids.isEmpty()) {
            return;
        }

        process(txContext, field("worker_uid").in(inactiveWorkerUids), consumer);
    }

    @Override
    public void processWorkerJobsForDeadWorker(TransactionContext txContext, String workerUid, BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        process(txContext, field("worker_uid").eq(workerUid), consumer);
    }

    private void process(TransactionContext txContext, Condition condition, BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        // if both queue and repository support the same transaction type, we participate in the transaction, otherwise, not
        if (txContext.supports(JdbcTransactionContext.class)) {
            var dslContext = txContext.unwrap(JdbcTransactionContext.class).getDslContext();
            process(dslContext, txContext, condition, consumer);
        } else {
            this.jdbcRepository
                .getDslContextWrapper()
                .transaction(configuration -> process(DSL.using(configuration), txContext, condition, consumer));
        }
    }

    private void process(DSLContext dslContext,
        TransactionContext txContext,
        Condition condition,
        BiConsumer<TransactionContext, WorkerJobRunning> consumer) {
        dslContext
            .select(field("key"), field("value"))
            .from(this.jdbcRepository.getTable())
            .where(condition)
            .forUpdate()
            .fetch()
            .forEach(record ->
            {
                String key = record.get("key", String.class);
                WorkerJobRunning workerJobRunning = this.jdbcRepository.deserialize(record.get("value", String.class));

                if (workerJobRunning.isLegacy()) {
                    // Written by a worker of a previous major version: every consumer reads fields this
                    // entry does not carry, and a failure here aborts the whole liveness sweep — including
                    // the vNode rebalance that runs after it, which silently stops all trigger scheduling.
                    // The worker holding this lease is gone, so drop it on the same connection that holds
                    // the row lock rather than hand it to a consumer that cannot use it.
                    log.warn(
                        "Discarding running entry '{}' of type '{}': it was written by a previous version and cannot be processed by this one.",
                        key,
                        workerJobRunning.getType()
                    );
                    deleteByKey(dslContext, key);
                    return;
                }

                consumer.accept(txContext, workerJobRunning);
            });
    }
}
