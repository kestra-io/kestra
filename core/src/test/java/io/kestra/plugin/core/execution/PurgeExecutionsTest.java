package io.kestra.plugin.core.execution;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class PurgeExecutionsTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void run() throws Exception {
        // create an execution to delete
        String namespace = "run.namespace";
        String flowId = "run-flow-id";
        var execution = Execution.builder()
            .id(IdUtils.create())
            .namespace(namespace)
            .flowId(flowId)
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);

        var purge = PurgeExecutions.builder()
            .flowId(Property.of(flowId))
            .namespace(Property.of(namespace))
            .endDate(Property.of(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var runContext = runContextFactory.of(Map.of("flow", Map.of("namespace", namespace, "id", flowId)));
        var output = purge.run(runContext);

        assertThat(output.getExecutionsCount(), is(1));
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
            .state(new State().withState(State.Type.SUCCESS))
            .build();
        executionRepository.save(execution);
        executionRepository.delete(execution);

        var purge = PurgeExecutions.builder()
            .namespace(Property.of(namespace))
            .flowId(Property.of(flowId))
            .endDate(Property.of(ZonedDateTime.now().plusMinutes(1).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)))
            .build();
        var runContext = runContextFactory.of(Map.of("flow", Map.of("namespace", namespace, "id", flowId)));
        var output = purge.run(runContext);

        assertThat(output.getExecutionsCount(), is(1));
    }
}