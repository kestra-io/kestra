package io.kestra.jdbc.runner;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.tasks.flows.Subflow;
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
public abstract class AbstractSubflowExecutionTest {
    @Inject
    AbstractJdbcSubflowExecutionStorage subflowExecutionStorage;

    @Inject
    JdbcTestUtils jdbcTestUtils;

    @Test
    void suite() throws Exception {

        SubflowExecution<?> workerTaskExecution = SubflowExecution.builder()
            .execution(Execution.builder().id(IdUtils.create()).build())
            .parentTask(Subflow.builder().type(Subflow.class.getName()).id(IdUtils.create()).build())
            .parentTaskRun(TaskRun.builder().id(IdUtils.create()).build())
            .build();

        subflowExecutionStorage.save(List.of(workerTaskExecution));


        Optional<SubflowExecution<?>> find = subflowExecutionStorage.get(workerTaskExecution.getExecution().getId());
        assertThat(find.isPresent(), is(true));
        assertThat(find.get().getExecution().getId(), is(workerTaskExecution.getExecution().getId()));


        subflowExecutionStorage.delete(workerTaskExecution);

        find = subflowExecutionStorage.get(workerTaskExecution.getExecution().getId());
        assertThat(find.isPresent(), is(false));
    }

    @BeforeEach
    protected void init() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }
}