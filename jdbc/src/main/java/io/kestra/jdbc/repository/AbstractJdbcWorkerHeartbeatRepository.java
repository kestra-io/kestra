package io.kestra.jdbc.repository;

import io.kestra.core.repositories.WorkerHeartbeatRepositoryInterface;
import io.kestra.core.runners.WorkerHeartbeat;
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
public abstract class AbstractJdbcWorkerHeartbeatRepository extends AbstractJdbcRepository implements WorkerHeartbeatRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerHeartbeat> jdbcRepository;

    public AbstractJdbcWorkerHeartbeatRepository(io.kestra.jdbc.AbstractJdbcRepository<WorkerHeartbeat> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Value("${kestra.heartbeat.frequency}")
    private Duration frequency;

    @Value("${kestra.heartbeat.heartbeat-missed}")
    private Integer nbMissed;

    @Override
    public Optional<WorkerHeartbeat> findByWorkerUuid(String workerUuid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = this.heartbeatSelectQuery(configuration, workerUuid);

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public Optional<WorkerHeartbeat> heartbeatCheckUp(String workerUuid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(configuration, workerUuid)
                        .forUpdate();

                Optional<WorkerHeartbeat> workerHeartbeat = this.jdbcRepository.fetchOne(select);

                if (workerHeartbeat.isPresent()) {
                    this.save(workerHeartbeat.get().toBuilder().status(WorkerHeartbeat.Status.UP).heartbeatDate(Instant.now()).build());
                    return workerHeartbeat;
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
                        .and(field("status").eq(WorkerHeartbeat.Status.UP.toString()))
                        // We consider a heartbeat late if it's older than heartbeat missed times the frequency
                        .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(getNbMissed() * getFrequency().getSeconds())))
                        .forUpdate();

                Optional<WorkerHeartbeat> workerHeartbeat = this.jdbcRepository.fetchOne(select);

                workerHeartbeat.ifPresent(heartbeat -> {
                    heartbeat.setStatus(WorkerHeartbeat.Status.DEAD);
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
                        .and(field("status").eq(WorkerHeartbeat.Status.DEAD.toString()))
                        .and(field("heartbeat_date").lessThan(Instant.now().minusSeconds(2 * getNbMissed() * getFrequency().getSeconds())))
                        .forUpdate();

                Optional<WorkerHeartbeat> workerHeartbeat = this.jdbcRepository.fetchOne(select);

                if(workerHeartbeat.isPresent()) {
                    this.delete(workerHeartbeat.get());
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
    public List<WorkerHeartbeat> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectWhereStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration);

                return this.jdbcRepository.fetch(select);
            });
    }

    public List<WorkerHeartbeat> findAllAlive() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration)
                    .where(field("status").eq(WorkerHeartbeat.Status.UP.toString()));

                return this.jdbcRepository.fetch(select);
            });
    }

    public List<WorkerHeartbeat> findAllDead() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select =
                    this.heartbeatSelectAllQuery(configuration)
                    .where(field("status").eq(WorkerHeartbeat.Status.DEAD.toString()));

                return this.jdbcRepository.fetch(select);
            });
    }

    public void delete(WorkerHeartbeat workerHeartbeat) {
        this.jdbcRepository.delete(workerHeartbeat);
    }

    @Override
    public WorkerHeartbeat save(WorkerHeartbeat workerHeartbeat) {
        this.jdbcRepository.persist(workerHeartbeat, this.jdbcRepository.persistFields(workerHeartbeat));
        return workerHeartbeat;
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
