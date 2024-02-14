package io.kestra.jdbc.repository;

import io.kestra.core.repositories.WorkerInstanceRepositoryInterface;
import io.kestra.core.runners.WorkerInstance;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Singleton
@Getter
@Slf4j
public abstract class AbstractJdbcWorkerInstanceRepository extends AbstractJdbcRepository implements WorkerInstanceRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerInstance> jdbcRepository;

    public AbstractJdbcWorkerInstanceRepository(io.kestra.jdbc.AbstractJdbcRepository<WorkerInstance> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Value("${kestra.heartbeat.frequency}")
    private Duration frequency;

    @Value("${kestra.heartbeat.heartbeat-missed}")
    private Integer nbMissed;

    @Override
    public Optional<WorkerInstance> findByWorkerUuid(String workerUuid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = this.heartbeatSelectQuery(DSL.using(configuration), workerUuid);

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public Optional<WorkerInstance> heartbeatCheckUp(String workerUuid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(DSL.using(configuration), workerUuid)
                        .forUpdate();

                Optional<WorkerInstance> workerInstance = this.jdbcRepository.fetchOne(select);

                if (workerInstance.isPresent()) {
                    WorkerInstance updatedWorker = workerInstance.get().toBuilder().status(WorkerInstance.Status.UP).heartbeatDate(Instant.now()).build();
                    this.save(updatedWorker);
                    return Optional.of(updatedWorker);
                }
                return Optional.empty();
            });
    }

    public void heartbeatStatusUpdate(String workerUuid, DSLContext context) {
        SelectForUpdateOfStep<Record1<Object>> select =
            this.heartbeatSelectQuery(context, workerUuid)
                .and(field("status").eq(WorkerInstance.Status.UP.toString()))
                // We consider a heartbeat dead if it's older than heartbeat missed times the frequency
                .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(getNbMissed() * getFrequency().getSeconds())))
                .forUpdate();

        Optional<WorkerInstance> workerInstance = this.jdbcRepository.fetchOne(select);

        workerInstance.ifPresent(heartbeat -> {
            heartbeat.setStatus(WorkerInstance.Status.DEAD);

            log.warn("Detected non-responding worker, stated to DEAD: {}", heartbeat);

            this.jdbcRepository.persist(heartbeat, context,  this.jdbcRepository.persistFields(heartbeat));
        });
    }

    public void heartbeatsStatusUpdate(DSLContext context) {
        this.findAllAlive(context).forEach(heartbeat -> {
                this.heartbeatStatusUpdate(heartbeat.getWorkerUuid().toString(), context);
            }
        );
    }

    public void lockedWorkersUpdate(Function<DSLContext, Void> function) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                // Update all workers status
                heartbeatsStatusUpdate(context);

                function.apply(context);

                return null;
            });
    }

    public List<WorkerInstance> findAll(DSLContext context) {
        return this.jdbcRepository.fetch(this.heartbeatSelectAllQuery(context));
    }

    public List<WorkerInstance> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                return this.jdbcRepository.fetch(this.heartbeatSelectAllQuery(context));
            });
    }

    public List<WorkerInstance> findAllAlive(DSLContext context) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> this.jdbcRepository.fetch(
                this.heartbeatSelectAllQuery(context)
                    .where(field("status").eq(WorkerInstance.Status.UP.toString()))
            ));
    }

    public List<WorkerInstance> findAllToDelete(DSLContext context) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> this.jdbcRepository.fetch(
                this.heartbeatSelectAllQuery(context)
                    .where(field("status").eq(WorkerInstance.Status.DEAD.toString()))
                    .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(2 * getNbMissed() * getFrequency().getSeconds())))
            ));
    }

    public void delete(DSLContext context, WorkerInstance workerInstance) {
        this.jdbcRepository.delete(context, workerInstance);
    }

    @Override
    public void delete(WorkerInstance workerInstance) {
        this.jdbcRepository.delete(workerInstance);
    }

    @Override
    public WorkerInstance save(WorkerInstance workerInstance) {
        this.jdbcRepository.persist(workerInstance, this.jdbcRepository.persistFields(workerInstance));
        return workerInstance;
    }

    private SelectConditionStep<Record1<Object>> heartbeatSelectQuery(DSLContext context, String workerUuid) {
        return context
            .select(field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                field("worker_uuid").eq(workerUuid)
            );
    }

    private SelectJoinStep<Record1<Object>> heartbeatSelectAllQuery(DSLContext dsl) {
        return dsl.select(field("value"))
            .from(this.jdbcRepository.getTable());
    }

}
