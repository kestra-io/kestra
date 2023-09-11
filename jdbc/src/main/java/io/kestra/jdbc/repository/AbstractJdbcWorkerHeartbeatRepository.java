package io.kestra.jdbc.repository;

import io.kestra.core.repositories.WorkerHeartbeatRepositoryInterface;
import io.kestra.core.runners.WorkerHeartbeat;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import lombok.Getter;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.SelectForUpdateOfStep;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Singleton
@Getter
public abstract class AbstractJdbcWorkerHeartbeatRepository extends AbstractJdbcRepository implements WorkerHeartbeatRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerHeartbeat> jdbcRepository;

    public AbstractJdbcWorkerHeartbeatRepository(io.kestra.jdbc.AbstractJdbcRepository<WorkerHeartbeat> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Value("${kestra.heartbeat.frequency}")
    private Integer frequency;

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
                    if (workerHeartbeat.get().getStatus().equals(WorkerHeartbeat.Status.DEAD)) {
                        return Optional.empty();
                    }
                    this.save(workerHeartbeat.get().toBuilder().heartbeatDate(Timestamp.from(Instant.now())).build());
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
                    .forUpdate();

                Optional<WorkerHeartbeat> workerHeartbeat = this.jdbcRepository.fetchOne(select);

                workerHeartbeat.ifPresent(heartbeat -> {
                    // We consider a heartbeat late if it's older than heartbeat missed times the frequency
                    if (heartbeat.getHeartbeatDate().before(Timestamp.from(Instant.now().minusSeconds(getNbMissed() * getFrequency())))) {
                        heartbeat.setStatus(WorkerHeartbeat.Status.DEAD);
                        this.jdbcRepository.persist(heartbeat, this.jdbcRepository.persistFields(heartbeat));
                    }
                });
            });
    }

    public void heartbeatsStatusUpdate() {
        this.findAll().forEach(heartbeat -> {
                this.heartbeatStatusUpdate(heartbeat.getWorkerUuid().toString());
            }
        );
    }

    public void heartbeatCleanUp(String workerUuid) {
        this.jdbcRepository
            .getDslContextWrapper()
            .transaction(configuration -> {
                SelectForUpdateOfStep<Record1<Object>> select =
                    this.heartbeatSelectQuery(configuration, workerUuid)
                    .forUpdate();

                Optional<WorkerHeartbeat> workerHeartbeat = this.jdbcRepository.fetchOne(select);

                workerHeartbeat.ifPresent(heartbeat -> {
                    if (heartbeat.getStatus().equals(WorkerHeartbeat.Status.DEAD)
                        // we delete worker that have dead status more than two times the times considered to be dead
                        && heartbeat.getHeartbeatDate().before(Timestamp.from(Instant.now().minusSeconds(2 * getNbMissed() * getFrequency())))) {
                        this.delete(heartbeat);
                    }
                });
            });
    }

    public void heartbeatsCleanup() {
        this.findAll().forEach(heartbeat -> {
                this.heartbeatCleanUp(heartbeat.getWorkerUuid().toString());
            }
        );
    }


    @Override
    public List<WorkerHeartbeat> findAll() {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectJoinStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select(field("value"))
                    .from(this.jdbcRepository.getTable());

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

}
