package io.kestra.scheduler.vnodes;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.lock.LockService;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.vnodes.VNodeController;
import io.kestra.core.scheduler.vnodes.VNodesAssigner;
import io.kestra.core.scheduler.vnodes.internals.DefaultVNodesAssigner;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import io.kestra.scheduler.utils.InMemorySchedulerEventQueue;
import io.kestra.scheduler.utils.TestVNodeAssignmentListener;

import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VNodeControllerTest {

    private InMemorySchedulerEventQueue eventQueue;
    private ServiceLivenessStore serviceLivenessStore;
    private LockService lockService;

    @BeforeEach
    public void beforeEach() {
        this.lockService = mock(LockService.class);
        this.serviceLivenessStore = mock(ServiceLivenessStore.class);
        this.eventQueue = new InMemorySchedulerEventQueue();
    }

    @Test
    void shouldAcquireLockWhenCheckServicesGivenNoLeader() {
        // GIVEN
        VNodeController controller = createVNodeController();
        Disposable mockLock = mock(Disposable.class);
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.of(mockLock));

        // WHEN
        controller.checkServicesAndRebalanceVNodes();

        // THEN
        verify(lockService, times(1)).tryLock("vnodes", "controller");
    }

    @Test
    void shouldNotRebalanceWhenNoLockGivenNotLeader() {
        // GIVEN
        VNodeController controller = createVNodeController();
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.empty());

        // WHEN
        controller.checkServicesAndRebalanceVNodes();

        // THEN
        assertThat(eventQueue.sentEvents()).isEmpty();
    }

    @Test
    void shouldRebalanceVNodesWhenActiveSchedulersChangedGivenLeaderLock() {
        // GIVEN
        VNodeController controller = createVNodeController();
        Disposable mockLock = mock(Disposable.class);
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.of(mockLock));

        ServiceInstance s1 = new ServiceInstance("scheduler-1", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        ServiceInstance s2 = new ServiceInstance("scheduler-2", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        when(serviceLivenessStore.findAllInstancesInStates(anySet())).thenReturn(List.of(s1, s2));

        DefaultVNodesAssigner assigner = new DefaultVNodesAssigner(eventQueue);
        try {
            assigner.start();
            assigner.subscribe("scheduler-1", VNodesAssigner.VNodeAssignmentListener.NOOP);
            assigner.subscribe("scheduler-2", VNodesAssigner.VNodeAssignmentListener.NOOP);

            // WHEN
            controller.checkServicesAndRebalanceVNodes();

            // THEN
            List<SchedulerEvent> events = eventQueue.sentEvents();
            assertThat(events).hasSize(4);
            SchedulerEvent first = events.getFirst();
            assertThat(first).isInstanceOf(SchedulerEvent.VNodesAssignmentRequest.class);
            assertThat(((SchedulerEvent.VNodesAssignmentRequest) first).schedulers()).containsExactlyInAnyOrder("scheduler-1", "scheduler-2");

            SchedulerEvent second = events.getLast();
            assertThat(second).isInstanceOf(SchedulerEvent.VNodesAssignmentRelease.class);
            assertThat(((SchedulerEvent.VNodesAssignmentRelease) second).assignments()).hasSize(2);
        } finally {
            assigner.stop();
        }
    }

    @Test
    void shouldRebalanceVNodesWithEmptyAssignmentsWhenActiveSchedulersDoNotSendReply() {
        // GIVEN
        VNodeController controller = createVNodeController();
        Disposable mockLock = mock(Disposable.class);
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.of(mockLock));

        ServiceInstance s1 = new ServiceInstance("scheduler-1", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        ServiceInstance s2 = new ServiceInstance("scheduler-2", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        when(serviceLivenessStore.findAllInstancesInStates(anySet())).thenReturn(List.of(s1, s2));

        // WHEN
        controller.checkServicesAndRebalanceVNodes();

        // THEN
        List<SchedulerEvent> events = eventQueue.sentEvents();
        assertThat(events).hasSize(2);
        SchedulerEvent first = events.getFirst();
        assertThat(first).isInstanceOf(SchedulerEvent.VNodesAssignmentRequest.class);
        assertThat(((SchedulerEvent.VNodesAssignmentRequest) first).schedulers()).containsExactlyInAnyOrder("scheduler-1", "scheduler-2");

        SchedulerEvent second = events.getLast();
        assertThat(second).isInstanceOf(SchedulerEvent.VNodesAssignmentRelease.class);
        assertThat(((SchedulerEvent.VNodesAssignmentRelease) second).assignments()).isEmpty();
    }

    @Test
    void shouldRebalanceEachServiceTypeOnItsOwnWhenAnotherServiceTypeSharesTheVNodes() {
        // GIVEN
        VNodeController controller = new VNodeController(
            lockService,
            serviceLivenessStore,
            eventQueue,
            new SchedulerConfiguration(16, Duration.ofSeconds(1), 0),
            List.of(new VNodeController.VNodesGroup(ServiceType.EXECUTOR))
        );
        when(lockService.tryLock(anyString(), anyString())).thenAnswer(invocation -> Optional.of(mock(Disposable.class)));

        ServiceInstance scheduler = new ServiceInstance("scheduler-1", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        ServiceInstance executor = new ServiceInstance("executor-1", ServiceType.EXECUTOR, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        when(serviceLivenessStore.findAllInstancesInStates(anySet())).thenReturn(List.of(scheduler, executor));

        TestVNodeAssignmentListener schedulerListener = new TestVNodeAssignmentListener();
        TestVNodeAssignmentListener executorListener = new TestVNodeAssignmentListener();
        DefaultVNodesAssigner assigner = new DefaultVNodesAssigner(eventQueue);
        try {
            assigner.start();
            assigner.subscribe("scheduler-1", schedulerListener);
            assigner.subscribe(ServiceType.EXECUTOR, "executor-1", executorListener);

            // WHEN
            controller.checkServicesAndRebalanceVNodes();

            // THEN
            verify(lockService, times(1)).tryLock("vnodes", "controller-executor");
            assertThat(schedulerListener.revokedCount()).isEqualTo(1);
            assertThat(schedulerListener.assigned()).containsExactly(Set.copyOf(IntStream.range(0, 16).boxed().toList()));
            assertThat(executorListener.revokedCount()).isEqualTo(1);
            assertThat(executorListener.assigned()).containsExactly(Set.copyOf(IntStream.range(0, 16).boxed().toList()));

            List<SchedulerEvent.VNodesAssignmentRequest> requests = eventQueue.sentEvents().stream()
                .filter(SchedulerEvent.VNodesAssignmentRequest.class::isInstance)
                .map(SchedulerEvent.VNodesAssignmentRequest.class::cast)
                .toList();
            assertThat(requests).extracting(SchedulerEvent.VNodesAssignmentRequest::schedulers, SchedulerEvent.VNodesAssignmentRequest::serviceType)
                .containsExactly(tuple(Set.of("scheduler-1"), null), tuple(Set.of("executor-1"), ServiceType.EXECUTOR));
        } finally {
            assigner.stop();
        }
    }

    @Test
    void shouldDisposeLockWhenClosedGivenLeaderLock() {
        // GIVEN
        VNodeController controller = createVNodeController();
        Disposable mockLock = mock(Disposable.class);
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.of(mockLock));

        // WHEN
        controller.checkServicesAndRebalanceVNodes();
        controller.close();

        // THEN
        verify(mockLock, times(1)).dispose();
    }

    VNodeController createVNodeController() {
        return new VNodeController(
            lockService,
            serviceLivenessStore,
            eventQueue,
            new SchedulerConfiguration(16, Duration.ofSeconds(1), 0)
        );
    }
}