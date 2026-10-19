package io.kestra.core.scheduler.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContextLoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DefaultTriggerExecutionPublisherTest {

    @Test
    void shouldSetTraceParentOnCreateWhenEvaluationCarriesOne() throws Exception {
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

        ArgumentCaptor<ExecutionCommand> captor = ArgumentCaptor.forClass(ExecutionCommand.class);
        verify(queue).emit(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(Create.class);
        Create create = (Create) captor.getValue();
        assertThat(create.traceParent()).isEqualTo(traceParent);
    }
}
