package io.kestra.jdbc.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.repositories.WorkerJobRunningInterface;
import io.kestra.core.runners.WorkerJobRunning;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

import java.util.List;
import java.util.Optional;

@Singleton
@Slf4j
public abstract class AbstractJdbcWorkerJobRunningRepository extends AbstractJdbcRepository implements WorkerJobRunningInterface {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    protected io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository;

    public AbstractJdbcWorkerJobRunningRepository(io.kestra.jdbc.AbstractJdbcRepository<WorkerJobRunning> jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public WorkerJobRunning save(WorkerJobRunning workerJobRunning, DSLContext context) {
        this.jdbcRepository.persist(workerJobRunning, context, this.jdbcRepository.persistFields(workerJobRunning));
        return workerJobRunning;
    }

    @Override
    public void delete(String taskRunId) {
        Optional<WorkerJobRunning> workerJobRunning = this.findByTaskRunId(taskRunId);
        workerJobRunning.ifPresent(jobRunning -> this.jdbcRepository.delete(jobRunning));
    }

    @Override
    public Optional<WorkerJobRunning> findByTaskRunId(String taskRunId) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                SelectConditionStep<Record1<Object>> select = DSL
                    .using(configuration)
                    .select((field("value")))
                    .from(this.jdbcRepository.getTable())
                    .where(
                        field("taskrun_id").eq(taskRunId)
                    );

                return this.jdbcRepository.fetchOne(select);
            });
    }

    @Override
    public List<WorkerJobRunning> getWorkerJobWithWorkerDead(List<String> workersAlive) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> DSL
                .using(configuration)
                .select(field("value"))
                .from(this.jdbcRepository.getTable())
                .where(field("worker_uuid").notIn(workersAlive))
                .forUpdate()
                .fetch()
                .map(r -> {
                    WorkerJobRunning value;
                    try {
                        value = MAPPER.readValue(r.get("value").toString(), WorkerJobRunning.class);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    return value;
                })
            );
    }
}

