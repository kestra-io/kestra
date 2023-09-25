package io.kestra.jdbc.repository;

import io.kestra.core.repositories.WorkerInstanceRepositoryInterface;
import io.kestra.core.runners.WorkerInstance;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateOfStep;
import org.jooq.SelectWhereStep;
import org.jooq.impl.DSL;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Singleton
@Getter
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
                SelectConditionStep<Record1<Object>> select = this.heartbeatSelectQuery(configuration, workerUuid);

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public Optional<WorkerInstance> heartbeatCheckUp(String workerUuid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(configuration, workerUuid)
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

    public void heartbeatStatusUpdate(String workerUuid) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(configuration, workerUuid)
                        .and(field("status").eq(WorkerInstance.Status.UP.toString()))
                        // We consider a heartbeat late if it's older than heartbeat missed times the frequency
                        .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(getNbMissed() * getFrequency().getSeconds())))
                        .forUpdate();

                Optional<WorkerInstance> workerInstance = this.jdbcRepository.fetchOne(select);

                workerInstance.ifPresent(heartbeat -> {
                    heartbeat.setStatus(WorkerInstance.Status.DEAD);
                    this.jdbcRepository.persist(heartbeat, this.jdbcRepository.persistFields(heartbeat));
                });
            });
    }

    public void heartbeatsStatusUpdate() {
        this.findAllAlive().forEach(heartbeat -> {
                this.heartbeatStatusUpdate(heartbeat.getWorkerUuid().toString());
            }
        );
    }

    public Boolean heartbeatCleanUp(String workerUuid) {
        AtomicReference<Boolean> bool = new AtomicReference<>(false);
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(configuration, workerUuid)
                        // we delete worker that have dead status more than two times the times considered to be dead
                        .and(field("status").eq(WorkerInstance.Status.DEAD.toString()))
                        .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(2 * getNbMissed() * getFrequency().getSeconds())))
                        .forUpdate();

                Optional<WorkerInstance> workerInstance = this.jdbcRepository.fetchOne(select);

                if(workerInstance.isPresent()) {
                    this.delete(workerInstance.get());
                    bool.set(true);
                }
            });
        return bool.get();
    }

    public Boolean heartbeatsCleanup() {
        AtomicReference<Boolean> bool = new AtomicReference<>(false);
        this.findAllDead().forEach(heartbeat -> {
                bool.set(this.heartbeatCleanUp(heartbeat.getWorkerUuid().toString()));
            }
        );
        return bool.get();
    }

    @Override
    public List<WorkerInstance> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectWhereStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration);

                return this.jdbcRepository.fetch(select);
            });
    }

    public List<WorkerInstance> findAllAlive() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration)
                    .where(field("status").eq(WorkerInstance.Status.UP.toString()));

                return this.jdbcRepository.fetch(select);
            });
    }

    public List<WorkerInstance> findAllDead() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration)
                    .where(field("status").eq(WorkerInstance.Status.DEAD.toString()));

                return this.jdbcRepository.fetch(select);
            });
    }

    public void delete(WorkerInstance workerInstance) {
        this.jdbcRepository.delete(workerInstance);
    }

    @Override
    public WorkerInstance save(WorkerInstance workerInstance) {
        this.jdbcRepository.persist(workerInstance, this.jdbcRepository.persistFields(workerInstance));
        return workerInstance;
    }

    private SelectConditionStep<Record1<Object>> heartbeatSelectQuery(org.jooq.Configuration configuration, String workerUuid) {
        return DSL
            .using(configuration)
            .select(field("value"))
            .from(this.jdbcRepository.getTable())
            .where(
                field("worker_uuid").eq(workerUuid)
            );
    }

    private SelectWhereStep<Record1<Object>> heartbeatSelectAllQuery(org.jooq.Configuration configuration) {
        return DSL
            .using(configuration)
            .select(field("value"))
            .from(this.jdbcRepository.getTable());
    }

}
