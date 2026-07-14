package io.kestra.core.runners;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceType;
import io.kestra.core.worker.WorkerGroups;

/**
 * Benchmarks routing lookups backed by {@link WorkerQueueMetaStore.DefaultWorkerQueueMetaStore}.
 *
 * <p>
 * The uncached benchmark invalidates the snapshot before each lookup to represent the previous
 * repeated liveness-store read pattern. The cached benchmark keeps the routing snapshot warm and
 * measures the steady-state lookup path used during bursts of task routing decisions.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class WorkerQueueMetaStoreBenchmark {
    private static final String TARGET_QUEUE = "group-42";

    @Param({ "100", "1000" })
    private int workerCount;

    private SimpleServiceLivenessStore livenessStore;
    private WorkerQueueMetaStore.DefaultWorkerQueueMetaStore cachedMetaStore;
    private WorkerQueueMetaStore.DefaultWorkerQueueMetaStore uncachedMetaStore;

    @Setup(Level.Iteration)
    public void setup() {
        livenessStore = new SimpleServiceLivenessStore(workerInstances(workerCount));
        cachedMetaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore(livenessStore, Duration.ofMinutes(1));
        uncachedMetaStore = new WorkerQueueMetaStore.DefaultWorkerQueueMetaStore(livenessStore, Duration.ofMinutes(1));

        cachedMetaStore.hasActiveWorkerForQueue(TARGET_QUEUE);
    }

    /**
     * Steady-state routing lookup from a warm cache.
     */
    @Benchmark
    public boolean cachedLookup() {
        return cachedMetaStore.hasActiveWorkerForQueue(TARGET_QUEUE);
    }

    /**
     * Baseline routing lookup that reloads active worker instances before every decision.
     */
    @Benchmark
    public boolean uncachedLookup() {
        uncachedMetaStore.invalidate();
        return uncachedMetaStore.hasActiveWorkerForQueue(TARGET_QUEUE);
    }

    /**
     * Steady-state queue discovery from a warm cache, used by queue lag polling.
     */
    @Benchmark
    public int cachedListAllWorkerQueueIds() {
        return cachedMetaStore.listAllWorkerQueueIds().size();
    }

    /**
     * Baseline queue discovery that reloads active worker instances before every call.
     */
    @Benchmark
    public int uncachedListAllWorkerQueueIds() {
        uncachedMetaStore.invalidate();
        return uncachedMetaStore.listAllWorkerQueueIds().size();
    }

    private static List<ServiceInstance> workerInstances(int workerCount) {
        List<ServiceInstance> instances = new ArrayList<>(workerCount + 20);
        Instant now = Instant.now();

        for (int i = 0; i < workerCount; i++) {
            instances.add(serviceInstance("worker-" + i, ServiceType.WORKER, "group-" + (i % 100), now));
        }
        for (int i = 0; i < 20; i++) {
            instances.add(serviceInstance("executor-" + i, ServiceType.EXECUTOR, "group-" + i, now));
        }

        return List.copyOf(instances);
    }

    private static ServiceInstance serviceInstance(String id, ServiceType type, String workerGroupId, Instant now) {
        return new ServiceInstance(
            id,
            type,
            Service.ServiceState.RUNNING,
            null,
            now,
            now,
            null,
            null,
            Map.of(WorkerGroups.SERVICE_PROPS_KEY, workerGroupId),
            null
        );
    }

    private static final class SimpleServiceLivenessStore implements ServiceLivenessStore {
        private final List<ServiceInstance> instances;

        private SimpleServiceLivenessStore(List<ServiceInstance> instances) {
            this.instances = instances;
        }

        @Override
        public List<ServiceInstance> findAllInstancesInStates(Set<Service.ServiceState> states) {
            return instances;
        }

        @Override
        public List<ServiceInstance> findAllInstancesInState(Service.ServiceState state) {
            return instances;
        }
    }
}
