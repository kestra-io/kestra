package io.kestra.jdbc.repository;

import io.kestra.core.repositories.WorkerJobRunningRepositoryInterface;
import io.kestra.core.runners.WorkerJobRunning;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class AbstractJdbcWorkerJobRunningRepository extends AbstractJdbcRepository implements WorkerJobRunningRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository;

    public AbstractJdbcWorkerJobRunningRepository(io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public WorkerJobRunning save(WorkerJobRunning workerJobRunning, DSLContext context) {
        this.jdbcRepository.persist(workerJobRunning, context, this.jdbcRepository.persistFields(workerJobRunning));
        return workerJobRunning;
    }

    @Override
    public void deleteByKey(String uid) {
        Optional<WorkerJobRunning> workerJobRunning = this.findByKey(uid);
        workerJobRunning.ifPresent(jobRunning -> this.jdbcRepository.delete(jobRunning));
    }

    @Override
    public Optional<WorkerJobRunning> findByKey(String uid) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select((field("value")))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("key").eq(uid)
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    public List<WorkerJobRunning> getWorkerJobWithWorkerDead(DSLContext context, List<String> workersToDelete) {
        return context
                .select(field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("worker_uuid").in(workersToDelete))
                .forUpdate()
                .fetch()
                .map(r -> this.jdbcRepository.deserialize(r.get("value").toString())
            );
    }
}

