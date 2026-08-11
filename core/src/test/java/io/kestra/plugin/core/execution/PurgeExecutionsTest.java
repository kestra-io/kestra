package io.kestra.plugin.core.execution;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskOutput;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.TaskOutputRepositoryInterface;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PurgeExecutionsTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private TaskOutputRepositoryInterface taskOutputRepository;

    @Test
    void run() throws Exception {
        // create an execution to delete
        String namespace = "run.namespace";
        String flowId = "run-flow-id";
        String executionId = IdUtils.create();
        var execution = Execution.builder()
            .id(executionId)
            .namespace(namespace)
            .flowId(flowId)
            .tenantId(MAIN_TENANT)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);

        var taskOutput = new TaskOutput(IdUtils.create(), MAIN_TENANT, executionId, "Hello World".getBytes(), null);
        taskOutputRepository.save(taskOutput);

        var purge = PurgeExecutions.builder()
            .flowId(Property.ofValue(flowId))
            .namespace(Property.ofValue(namespace))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var runContext = runContextFactory.of(flowId, namespace);
        var output = purge.run(runContext);

        assertThat(output.getExecutionsCount()).isEqualTo(1);
        assertThat(output.getTaskOutputsCount()).isEqualTo(1);
    }

    @Test
    void deleted() throws Exception {
        String namespace = "deleted.namespace";
        String flowId = "deleted-flow-id";

        // create an execution to delete
        var execution = Execution.builder()
            .namespace(namespace)
            .flowId(flowId)
            .id(IdUtils.create())
            .tenantId(MAIN_TENANT)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);
        executionRepository.delete(execution);

        var purge = PurgeExecutions.builder()
            .namespace(Property.ofValue(namespace))
            .flowId(Property.ofValue(flowId))
            .endDate(Property.ofValue(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var runContext = runContextFactory.of(flowId, namespace);
        var output = purge.run(runContext);

        assertThat(output.getExecutionsCount()).isEqualTo(1);
    }
}