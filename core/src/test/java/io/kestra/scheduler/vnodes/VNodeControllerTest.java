package io.kestra.scheduler.vnodes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.lock.LockService;
import io.kestra.core.scheduler.events.SchedulerEvent;
import io.kestra.core.scheduler.vnodes.SchedulerVNodesAssignmentChannel;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import io.kestra.core.vnodes.VNodeController;
import io.kestra.core.vnodes.VNodesAssigner;
import io.kestra.core.vnodes.VNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssignmentEvent;
import io.kestra.core.vnodes.internals.DefaultVNodesAssigner;
import io.kestra.scheduler.utils.InMemorySchedulerEventQueue;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(controller).extracting("controllerEpoch").isNotNull();
    }

    @Test
    void shouldRebalanceOnlyTheServicesOfItsChannelWithItsOwnLock() {
        // GIVEN
        List<VNodesAssignmentEvent> sent = new ArrayList<>();
        VNodesAssignmentChannel controllers = new VNodesAssignmentChannel() {
            @Override
            public ServiceType serviceType() {
                return ServiceType.CONTROLLER;
            }

            @Override
            public String controllerLockId() {
                return "worker-controller";
            }

            @Override
            public void send(VNodesAssignmentEvent event) {
                sent.add(event);
            }

            @Override
            public Disposable subscribe(Consumer<VNodesAssignmentEvent> consumer) {
                return Disposable.of(() -> {});
            }
        };
        VNodeController controller = new VNodeController(lockService, serviceLivenessStore, controllers, 16, Duration.ZERO);
        when(lockService.tryLock(anyString(), anyString())).thenReturn(Optional.of(mock(Disposable.class)));
        ServiceInstance scheduler = new ServiceInstance("scheduler-1", ServiceType.SCHEDULER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        ServiceInstance workerController = new ServiceInstance("controller-1", ServiceType.CONTROLLER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), null, null, null, null);
        when(serviceLivenessStore.findAllInstancesInStates(anySet())).thenReturn(List.of(scheduler, workerController));

        // WHEN
        controller.checkServicesAndRebalanceVNodes();

        // THEN
        verify(lockService).tryLock("vnodes", "worker-controller");
        assertThat(sent.getFirst()).isInstanceOfSatisfying(
            VNodesAssignmentEvent.VNodesAssignmentRequest.class,
            request -> assertThat(request.services()).containsExactly("controller-1")
        );
        assertThat(eventQueue.sentEvents()).isEmpty();
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

        DefaultVNodesAssigner assigner = new DefaultVNodesAssigner(new SchedulerVNodesAssignmentChannel(eventQueue));
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
            new SchedulerVNodesAssignmentChannel(eventQueue),
            16,
            Duration.ofSeconds(1)
        );
    }
}