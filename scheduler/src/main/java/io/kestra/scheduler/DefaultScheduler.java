package io.kestra.scheduler;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.scheduler.vnodes.VNodesAssigner;
import io.kestra.core.server.AbstractService;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.MaintenanceService;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Default {@link Scheduler} implementation.
 */
@Slf4j
@Singleton
@Primary
public class DefaultScheduler extends AbstractService implements Scheduler {

    private static final String EXECUTOR_NAME = "scheduler-scheduling-loop";

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExecutorsUtils executorsUtils;
    private final TriggerSchedulingLoopFactory schedulerEventLoopFactory;

    private ExecutorService executorService;
    private List<TriggerSchedulingLoop> schedulingLoops;
    private final VNodesAssigner vNodesAssigner;
    private final Clock clock;

    // Queues
    private final TriggerEventQueue triggerEventQueue;

    // Stores
    private final TriggerStateStore triggerStateStore;

    // Services
    private final MetricRegistry metricRegistry;

    private final MaintenanceService maintenanceService;

    // Consumers
    private final List<Disposable> consumerDisposables = new ArrayList<>();

    private Disposable maintenanceListener;

    private final Set<Integer> currentVNodesAssignment = new HashSet<>();

    private Disposable rebalanceDisposable;

    @Inject
    public DefaultScheduler(final TriggerSchedulingLoopFactory schedulerEventLoopFactory,
        final VNodesAssigner vNodesAssigner,
        final ExecutorsUtils executorsUtils,
        final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        final TriggerEventQueue triggerEventQueue,
        @Named("cached") final TriggerStateStore triggerStateStore,
        final MetricRegistry metricRegistry,
        final MaintenanceService maintenanceService) {
        this(schedulerEventLoopFactory, vNodesAssigner, executorsUtils, eventPublisher, triggerEventQueue, triggerStateStore, metricRegistry, maintenanceService, SchedulerClock.getClock());
    }

    @VisibleForTesting
    public DefaultScheduler(final TriggerSchedulingLoopFactory schedulerEventLoopFactory,
        final VNodesAssigner vNodesAssigner,
        final ExecutorsUtils executorsUtils,
        final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        final TriggerEventQueue triggerEventQueue,
        final TriggerStateStore triggerStateStore,
        final MetricRegistry metricRegistry,
        final MaintenanceService maintenanceService,
        final Clock clock) {
        super(ServiceType.SCHEDULER, eventPublisher);
        this.schedulerEventLoopFactory = schedulerEventLoopFactory;
        this.executorsUtils = executorsUtils;
        this.vNodesAssigner = vNodesAssigner;
        this.triggerEventQueue = triggerEventQueue;
        this.triggerStateStore = triggerStateStore;
        this.metricRegistry = metricRegistry;
        this.maintenanceService = maintenanceService;
        this.clock = clock;
        this.setState(ServiceState.CREATED);
    }

    /**
     * Gets the {@link Clock} attached to this scheduler.
     *
     * @return the {@link Clock}
     */
    public Clock clock() {
        return this.clock;
    }

    /**
     * Gets the set of vNodes currently assigned to this scheduler.
     *
     * @return the vNodes.
     */
    public Set<Integer> currentVNodesAssignment() {
        return this.currentVNodesAssignment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(int maxThreads) {
        if (!this.started.compareAndSet(false, true)) {
            throw new IllegalStateException("Scheduler already started");
        }
        this.metricRegistry.gauge(MetricRegistry.METRIC_SCHEDULER_EVENTLOOP_THREAD_MAX, MetricRegistry.METRIC_SCHEDULER_EVENTLOOP_THREAD_MAX_DESCRIPTION, maxThreads);

        // Create the scheduling loops
        this.executorService = executorsUtils.maxCachedThreadPool(maxThreads, EXECUTOR_NAME);

        this.schedulingLoops = new ArrayList<>(maxThreads);

        final AtomicInteger metricAssignedVNodesCount = this.metricRegistry.gauge(
            MetricRegistry.METRIC_SCHEDULER_ASSIGNED_VNODES_COUNT,
            MetricRegistry.METRIC_SCHEDULER_ASSIGNED_VNODES_COUNT_DESCRIPTION, new AtomicInteger(0)
        );

        // Subscribe to trigger vNodes assignment/revocation
        this.rebalanceDisposable = vNodesAssigner.subscribe(this.getId(), new VNodesAssigner.VNodeAssignmentListener() {
            @Override
            public void onVNodesRevoked() {
                if (!getState().isRunning()) {
                    return; // scheduler is either terminating or already terminated.
                }

                metricAssignedVNodesCount.set(0);

                // Stop the WorkerTriggerResult/TriggerEvent Queues consumption
                stopAllConsumers();

                // Stop all scheduling loops
                stopAllSchedulingLoop(true);
            }

            @Override
            public void onVNodesAssigned(Set<Integer> vNodes) {
                if (!getState().isRunning()) {
                    return; // scheduler is either terminating or already terminated.
                }

                metricAssignedVNodesCount.set(vNodes.size());

                final int numSchedulingLoop = Math.min(maxThreads, vNodes.size());

                // (Re)initialize trigger state store for assigned VNodes
                triggerStateStore.init(vNodes);

                // (Re)create TriggerSchedulingLoop
                for (int i = 0; i < numSchedulingLoop; i++) {
                    TriggerSchedulingLoop schedulingLoop = schedulerEventLoopFactory.create(i, clock);
                    schedulingLoops.add(schedulingLoop);
                }

                // Assign scheduling-loops to VNodes
                schedulingLoops.forEach(schedulingLoop ->
                {
                    // Compute vNodes assignments for the current event-loop
                    Set<Integer> assignments = vNodes.stream()
                        .filter(vNodeId -> vNodeId % maxThreads == schedulingLoop.id())
                        .collect(Collectors.toSet());
                    schedulingLoop.setAssignments(assignments);
                });

                currentVNodesAssignment.addAll(vNodes);

                // Restart scheduling only if not in maintenance mode
                if (!maintenanceService.isInMaintenanceMode()) {
                    startScheduling();
                }
            }
        });

        maintenanceListener = maintenanceService.listen(new MaintenanceService.MaintenanceListener() {
            @Override
            public void onMaintenanceModeEnter() {
                // vNode assignments may change during maintenance mode (e.g., a scheduler leaves or joins the cluster).
                // it's therefore more reliable to just stop scheduling in a similar way to vNodes revokation. 
                stopAllConsumers();
                stopAllSchedulingLoop(false);
                setState(ServiceState.MAINTENANCE);
            }

            @Override
            public void onMaintenanceModeExit() {
                // restart scheduling
                startScheduling();
                setState(ServiceState.RUNNING);
            }
        });

        if (maintenanceService.isInMaintenanceMode()) {
            setState(ServiceState.MAINTENANCE);
        } else {
            setState(ServiceState.RUNNING);
        }
        log.info("Scheduler started with {} thread(s) [timezone={}]", maxThreads, SchedulerClock.getClock().getZone());
    }

    private void startScheduling() {
        if (schedulingLoops.isEmpty()) {
            return; // nothing to start
        }

        // (Re)start the Queues consumption
        startTriggerEventConsumers();

        // (Re)submit all scheduling loops
        schedulingLoops.forEach(executorService::execute);
    }

    private void stopAllSchedulingLoop(boolean clearAssignment) {
        if (schedulingLoops.isEmpty()) {
            return; // quick path
        }

        List<CompletableFuture<Void>> pausable = schedulingLoops.stream()
            .filter(TriggerSchedulingLoop::isRunning)
            .map(schedulingLoop -> schedulingLoop.doOnEndLoop(() ->
            {
                // Pause the scheduling loop
                schedulingLoop.pause();

                // Ensure all the trigger events for this scheduling loop are processed
                schedulingLoop.processTriggerEvents();

                // Revoke all vNodes assignment
                schedulingLoop.setAssignments(Set.of());
            })).toList();

        // Wait for all scheduling loop to be effectively paused
        if (!pausable.isEmpty()) {
            CompletableFuture.allOf(pausable.toArray(new CompletableFuture<?>[0])).join();
        }

        // Stop and remove all scheduling loop
        schedulingLoops.forEach(TriggerSchedulingLoop::stop);

        if (clearAssignment) {
            // Clear local assignments
            schedulingLoops.clear();
            currentVNodesAssignment.clear();
        }
    }

    private void stopAllConsumers() {
        consumerDisposables.forEach(Disposable::dispose);
    }

    private void startTriggerEventConsumers() {
        Map<Integer, TriggerSchedulingLoop> schedulingLoopByVNode = getSchedulingLoopByVNode();

        consumerDisposables.add(triggerEventQueue.subscribe(currentVNodesAssignment, (vNode, events) ->
        {
            // Get the scheduling-loop for the event vNode.
            TriggerSchedulingLoop schedulingLoop = schedulingLoopByVNode.get(vNode);
            if (schedulingLoop != null) {
                // Push the events to the scheduling-loop
                CompletableFuture<Void> future = schedulingLoop.addTriggerEvents(vNode, events);

                // Wait for the completion to guarantee that when this method returns, all events are processed.
                future.join();
            } else {
                log.error("Received trigger events for a non assigned vNode [{}]. Event skipped.", vNode);
            }
        }));
    }

    /**
     * Convenience method to get all {@link TriggerSchedulingLoop} keyed by vNodes.
     *
     * @return the scheduling-loop keyed by vNode.
     */
    private Map<Integer, TriggerSchedulingLoop> getSchedulingLoopByVNode() {
        return schedulingLoops.stream()
            .flatMap(
                schedulingLoop -> schedulingLoop.assignments()
                    .stream()
                    .map(vNodeAssignment -> Map.entry(vNodeAssignment, schedulingLoop))
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ServiceState doStop() {
        if (!this.started.compareAndSet(true, false)) {
            return ServiceState.TERMINATED_GRACEFULLY; // Already shut down or not started.
        }

        if (rebalanceDisposable != null) {
            rebalanceDisposable.dispose();
        }

        // Stop all queues consumption
        stopAllConsumers();

        if (this.maintenanceListener != null) {
            this.maintenanceListener.dispose();
        }

        // Stop all scheduling loops
        stopAllSchedulingLoop(true);

        // Initiate graceful shutdown
        this.executorService.shutdown();

        // Wait for all TriggerSchedulingLoop to terminate
        boolean terminated;
        try {
            terminated = this.executorService.awaitTermination(30, TimeUnit.SECONDS);
            if (!terminated) {
                log.warn("Forcing scheduler shutdown...");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorService.shutdownNow();
            terminated = false;
            log.warn("Interrupted while stopping scheduler. Forced shutdown initiated.");
        }

        if (!terminated) {
            log.warn("Scheduler still has pending loops after shutdown. Forced termination completed.");
        }
        return terminated ? ServiceState.TERMINATED_GRACEFULLY : ServiceState.TERMINATED_FORCED;
    }

    @VisibleForTesting
    List<TriggerSchedulingLoop> schedulingLoops() {
        return schedulingLoops;
    }

    /** {@inheritDoc} **/
    @Override
    public boolean isActive() {
        return !currentVNodesAssignment.isEmpty();
    }
}
