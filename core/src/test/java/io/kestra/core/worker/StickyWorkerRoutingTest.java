package io.kestra.core.worker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.vnodes.ControllerVNodesConfiguration;
import io.kestra.core.vnodes.VNodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StickyWorkerRoutingTest {

    private final ServiceLivenessStore store = mock(ServiceLivenessStore.class);
    private final StickyWorkerRouting routing = new StickyWorkerRouting(store, new ControllerVNodesConfiguration(16, Duration.ofSeconds(5)));

    @Test
    void shouldRouteToAVNodeAssignedToTheControllerHoldingTheWorker() {
        when(store.findAllInstancesInState(Service.ServiceState.RUNNING)).thenReturn(
            List.of(
                controller("controller-a", List.of("worker-1"), List.of(0, 1)),
                controller("controller-b", List.of("worker-2"), List.of(7, 3))
            )
        );

        WorkerJobEvent event = routing.route(mock(WorkerJob.class), null, "worker-2");

        assertThat(event.stickyRouting().workerId()).isEqualTo("worker-2");
        assertThat(event.key()).isEqualTo(StickyWorkerRouting.routingKey(List.of(3, 7).get(VNodes.computeVNode(2, "worker-2"))));
    }

    @Test
    void shouldSpreadTheWorkersOfAControllerOverItsVNodes() {
        List<String> workerIds = IntStream.range(0, 20).mapToObj(i -> "worker-" + i).toList();
        when(store.findAllInstancesInState(Service.ServiceState.RUNNING)).thenReturn(
            List.of(controller("controller-a", workerIds, List.of(0, 5, 9, 12)))
        );

        Set<String> keys = workerIds.stream()
            .map(workerId -> routing.route(mock(WorkerJob.class), null, workerId).key())
            .collect(Collectors.toSet());

        assertThat(keys).hasSizeGreaterThan(1)
            .isSubsetOf(Stream.of(0, 5, 9, 12).map(StickyWorkerRouting::routingKey).toList());
    }

    @Test
    void shouldRouteToTheWorkerVNodeWhenNoControllerHoldsTheWorker() {
        when(store.findAllInstancesInState(Service.ServiceState.RUNNING)).thenReturn(
            List.of(controller("controller-a", List.of("worker-1"), List.of(0, 1)))
        );

        WorkerJobEvent event = routing.route(mock(WorkerJob.class), null, "worker-2");

        assertThat(event.key()).isEqualTo(StickyWorkerRouting.routingKey(VNodes.computeVNode(16, "worker-2")));
    }

    @Test
    void shouldLocateAWorkerThatLeftTheRunningStatesAsLostAndARunningOneNoControllerListsAsUnknown() {
        when(store.findAllInstancesInStates(Service.ServiceState.allRunningStates())).thenReturn(
            List.of(controller("controller-a", List.of("worker-1"), List.of(0, 1)), worker("worker-2"))
        );

        assertThat(routing.locate("worker-1")).isInstanceOf(StickyWorkerRouting.WorkerLocation.Held.class);
        assertThat(routing.locate("worker-2")).isInstanceOf(StickyWorkerRouting.WorkerLocation.Unknown.class);
        assertThat(routing.locate("disconnected-worker")).isInstanceOf(StickyWorkerRouting.WorkerLocation.Lost.class);
    }

    private static ServiceInstance worker(String id) {
        return new ServiceInstance(id, ServiceType.WORKER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), List.of(), null, null, Set.of());
    }

    private static ServiceInstance controller(String id, List<String> workerIds, List<Integer> vNodes) {
        return new ServiceInstance(
            id, ServiceType.CONTROLLER, Service.ServiceState.RUNNING, null, Instant.now(), Instant.now(), List.of(), null,
            Map.of(StickyWorkerRouting.WORKERS_PROP, workerIds, StickyWorkerRouting.VNODES_PROP, vNodes), Set.of()
        );
    }
}
