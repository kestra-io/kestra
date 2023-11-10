package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.tasks.flows.Flow;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest(transactional = false)
public abstract class AbstractWorkerTaskExecutionTest {
    @Inject
    AbstractJdbcWorkerTaskExecutionStorage workerTaskExecutionStorage;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void suite() throws Exception {

        WorkerTaskExecution<?> workerTaskExecution = WorkerTaskExecution.builder()
            .execution(Execution.builder().id(IdUtils.create()).build())
            .task(Flow.builder().type(Flow.class.getName()).id(IdUtils.create()).build())
            .taskRun(TaskRun.builder().id(IdUtils.create()).build())
            .build();

        workerTaskExecutionStorage.save(List.of(workerTaskExecution));


        Optional<WorkerTaskExecution<?>> find = workerTaskExecutionStorage.get(workerTaskExecution.getExecution().getId());
        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecution().getId(), is(workerTaskExecution.getExecution().getId()));


        workerTaskExecutionStorage.delete(workerTaskExecution);

        find = workerTaskExecutionStorage.get(workerTaskExecution.getExecution().getId());
        assertThat(find.isPresent(), is(false));
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}