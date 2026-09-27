package io.kestra.webserver.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.async.AsyncOperationProcessedEvent;
import io.kestra.core.async.AsyncOperationProcessedEvent.Outcome;
import io.kestra.core.async.AsyncOperationsConfiguration;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.server.AsyncOperationListener;
import io.kestra.core.server.AsyncOperationType;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.core.utils.IdUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TriggerStateServiceTest {
    private static final String TENANT = "main";
    private static final String NAMESPACE = "io.kestra.tests";
    private static final String FLOW_ID = "flow";
    private static final String TRIGGER_ID = "my-trigger";

    private TriggerRepositoryInterface triggerRepository;
    private FlowRepositoryInterface flowRepository;
    private TriggerEventQueue triggerEventQueue;
    private AsyncOperationWaiter asyncOperationWaiter;
    private RecordingAsyncOperationListener listener;
    private TriggerStateService triggerStateService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        triggerRepository = mock(TriggerRepositoryInterface.class);
        flowRepository = mock(FlowRepositoryInterface.class);
        triggerEventQueue = mock(TriggerEventQueue.class);
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue = mock(BroadcastQueueInterface.class);
        asyncOperationWaiter = mock(AsyncOperationWaiter.class);
        listener = new RecordingAsyncOperationListener();

        AbstractTrigger trigger = mock(AbstractTrigger.class);
        when(trigger.getId()).thenReturn(TRIGGER_ID);
        Flow flow = mock(Flow.class);
        when(flow.getTriggers()).thenReturn(List.of(trigger));
        when(flowRepository.findById(TENANT, NAMESPACE, FLOW_ID)).thenReturn(Optional.of(flow));

        triggerStateService = new TriggerStateService(
            triggerRepository,
            flowRepository,
            triggerEventQueue,
            executionKilledQueue,
            asyncOperationWaiter,
            new AsyncOperationsConfiguration(Duration.ofSeconds(5)),
            List.of(listener)
        );
    }

    @Test
    void shouldNotifyListenerOnceWhenUnlockingTriggersInBulk() {
        TriggerId triggerId = TriggerId.of(TENANT, NAMESPACE, FLOW_ID, TRIGGER_ID);
        when(triggerRepository.findByIdWithoutAcl(triggerId)).thenReturn(Optional.of(lockedTriggerState(triggerId)));

        triggerStateService.unlockAllByIds(List.of(triggerId));

        assertThat(listener.calls).hasSize(1);
        assertThat(listener.calls.getFirst().operationType()).isEqualTo(AsyncOperationType.TRIGGER_UNLOCK);
        assertThat(listener.calls.getFirst().itemCount()).isEqualTo(1);
    }

    @Test
    void shouldNotNotifyListenerWhenUnlockingASingleTrigger() throws Exception {
        TriggerId triggerId = TriggerId.of(TENANT, NAMESPACE, FLOW_ID, TRIGGER_ID);
        when(triggerRepository.findByIdWithoutAcl(triggerId)).thenReturn(Optional.of(lockedTriggerState(triggerId)));
        when(asyncOperationWaiter.submitAndWait(any(), any(), any())).thenReturn(
            new AsyncOperationProcessedEvent(IdUtils.create(), TENANT, triggerId.uid(), Outcome.SUCCEEDED, null, Instant.now())
        );

        triggerStateService.unlockTriggerById(triggerId);

        assertThat(listener.calls).isEmpty();
    }

    private static TriggerState lockedTriggerState(TriggerId triggerId) {
        return TriggerState.builder()
            .tenantId(triggerId.getTenantId())
            .namespace(triggerId.getNamespace())
            .flowId(triggerId.getFlowId())
            .triggerId(triggerId.getTriggerId())
            .locked(true)
            .type(TriggerType.POLLING)
            .build();
    }

    private record ListenerCall(String operationId, AsyncOperationType operationType, int itemCount) {
    }

    private static class RecordingAsyncOperationListener implements AsyncOperationListener {
        private final List<ListenerCall> calls = new ArrayList<>();

        @Override
        public void onAsyncOperationCreated(String operationId, AsyncOperationType operationType, int itemCount) {
            calls.add(new ListenerCall(operationId, operationType, itemCount));
        }
    }
}
