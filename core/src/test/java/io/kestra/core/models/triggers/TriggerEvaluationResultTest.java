package io.kestra.core.models.triggers;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.scheduler.service.DefaultTriggerExecutionPublisher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TriggerEvaluationResultTest {

    @Test
    void shouldRetainTraceParentFromExecution() {
        String traceParent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        Execution execution = Execution.builder()
            .id("exec-1")
            .tenantId("tenant-1")
            .namespace("ns-1")
            .flowId("flow-1")
            .flowRevision(1)
            .state(new State())
            .traceParent(traceParent)
            .build();

        TriggerEvaluationResult result = TriggerEvaluationResult.from(execution);
        assertThat(result.traceParent(), is(traceParent));

        TriggerId triggerId = TriggerId.of("tenant-1", "ns-1", "flow-1", "trig-1");
        Execution reconstructed = result.toExecution(triggerId);
        assertThat(reconstructed.getTraceParent(), is(traceParent));
    }

    @Test
    void shouldDefaultTraceParentToNullWhenAbsent() {
        Execution execution = Execution.builder()
            .id("exec-2")
            .tenantId("tenant-1")
            .namespace("ns-1")
            .flowId("flow-1")
            .flowRevision(1)
            .state(new State())
            .build();

        TriggerEvaluationResult result = TriggerEvaluationResult.from(execution);
        assertThat(result.traceParent(), nullValue());

        TriggerEvaluationResult legacyResult = new TriggerEvaluationResult(
            "exec-2",
            State.Type.CREATED,
            ExecutionTrigger.builder().id("trig-1").type("test").build(),
            Collections.emptyList(),
            1,
            null,
            Map.of()
        );
        assertThat(legacyResult.traceParent(), nullValue());
    }

    @Test
    void shouldPropagateTraceParentInDefaultTriggerExecutionPublisher() throws Exception {
        @SuppressWarnings("unchecked")
        DispatchQueueInterface<ExecutionCommand> queue = mock(DispatchQueueInterface.class);
        RunContextLoggerFactory loggerFactory = mock(RunContextLoggerFactory.class);
        DefaultTriggerExecutionPublisher publisher = new DefaultTriggerExecutionPublisher(queue, loggerFactory);

        String traceParent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";
        TriggerId triggerId = TriggerId.of("tenant-1", "ns-1", "flow-1", "trig-1");
        TriggerEvaluationResult evaluation = new TriggerEvaluationResult(
            "exec-3",
            State.Type.CREATED,
            ExecutionTrigger.builder().id("trig-1").type("test").build(),
            List.of(),
            1,
            null,
            Map.of(),
            traceParent
        );

        publisher.send(triggerId, evaluation);

        verify(queue).emit((ExecutionCommand) argThat(command ->
            command instanceof Create create && traceParent.equals(create.traceParent())
        ));
    }
}
