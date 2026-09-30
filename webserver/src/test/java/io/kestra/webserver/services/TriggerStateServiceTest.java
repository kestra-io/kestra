package io.kestra.webserver.services;

import java.time.Duration;
import java.time.Instant;
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
import io.kestra.core.server.AsyncOperationType;
import io.kestra.core.server.CoreAsyncOperationType;
import io.kestra.core.services.AsyncOperationWaiter;
import io.kestra.core.services.NotificationService;
import io.kestra.core.utils.IdUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private NotificationService notificationService;
    private TriggerStateService triggerStateService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        triggerRepository = mock(TriggerRepositoryInterface.class);
        flowRepository = mock(FlowRepositoryInterface.class);
        triggerEventQueue = mock(TriggerEventQueue.class);
        BroadcastQueueInterface<ExecutionKilled> executionKilledQueue = mock(BroadcastQueueInterface.class);
        asyncOperationWaiter = mock(AsyncOperationWaiter.class);
        notificationService = mock(NotificationService.class);

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
            notificationService
        );
    }

    @Test
    void shouldNotifyListenerOnceWhenUnlockingTriggersInBulk() {
        TriggerId triggerId = TriggerId.of(TENANT, NAMESPACE, FLOW_ID, TRIGGER_ID);
        when(triggerRepository.findByIdWithoutAcl(triggerId)).thenReturn(Optional.of(lockedTriggerState(triggerId)));

        triggerStateService.unlockAllByIds("test-user", TENANT, List.of(triggerId));

        verify(notificationService, times(1)).notifyAsyncOperation(eq("test-user"), eq(TENANT), any(), eq(CoreAsyncOperationType.TRIGGER_UNLOCK), eq(List.of(triggerId.uid())));
    }

    @Test
    void shouldNotNotifyListenerWhenUnlockingASingleTrigger() throws Exception {
        TriggerId triggerId = TriggerId.of(TENANT, NAMESPACE, FLOW_ID, TRIGGER_ID);
        when(triggerRepository.findByIdWithoutAcl(triggerId)).thenReturn(Optional.of(lockedTriggerState(triggerId)));
        when(asyncOperationWaiter.submitAndWait(any(), any(), any())).thenReturn(
            new AsyncOperationProcessedEvent(IdUtils.create(), TENANT, triggerId.uid(), Outcome.SUCCEEDED, null, Instant.now())
        );

        triggerStateService.unlockTriggerById(triggerId);

        verifyNoInteractions(notificationService);
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
}
