package io.kestra.scheduler;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.lock.LockService;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.SchedulerClock;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.SchedulerEventQueue;
import io.kestra.core.scheduler.TriggerEventQueue;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.kestra.core.server.ServiceLivenessStore;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.scheduler.vnodes.VNodeController;
import io.kestra.scheduler.internals.DefaultSchedulableTriggerFetcher;
import io.kestra.core.scheduler.vnodes.internals.DefaultVNodesAssigner;
import io.kestra.scheduler.internals.SchedulableEvaluator;
import io.kestra.scheduler.pubsub.TriggerWorkerJobPublisher;
import io.kestra.scheduler.pubsub.TriggerWorkerJobResultSubscriber;
import io.kestra.scheduler.stores.CachedFlowMetaStore;
import io.kestra.scheduler.stores.CachedTriggerStateStore;
import io.kestra.scheduler.stores.FlowMetaStore;
import io.kestra.scheduler.utils.InMemoryFlowMetaStore;
import io.kestra.scheduler.utils.InMemoryTriggerStateStore;
import io.kestra.scheduler.stores.TriggerStateStore;
import io.kestra.scheduler.utils.CollectorTriggerExecutionPublisher;
import io.kestra.scheduler.utils.InMemorySchedulerEventQueue;
import io.kestra.scheduler.utils.InMemoryTriggerEventQueue;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class DefaultSchedulerTest {
    
    private static final SchedulerConfiguration SCHEDULER_CONFIGURATION = new SchedulerConfiguration(16, Duration.ofSeconds(5), 100);
    
    @Inject
    MetricRegistry metricRegistry;
    
    @Inject
    RunContextFactory runContextFactory;
    
    @Inject
    ConditionService conditionService;
    
    @Inject
    PluginDefaultService pluginDefaultService;
    
    @Inject
    SchedulableEvaluator schedulableEvaluator;
    
    @Inject
    TriggerWorkerJobPublisher triggerWorkerJobPublisher;
    
    @Inject
    ExecutorsUtils executorsUtils;
    
    @Inject
    ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;
    
    @Inject
    private LockService lockService;
    
    // Stores
    private TriggerStateStore triggerStateStore;
    private InMemoryServiceLivenessStore serviceLivenessStore;
    private FlowMetaStore flowMetaStore;

    // Queues
    private SchedulerEventQueue schedulerEventQueue;
    private TriggerEventQueue triggerEventQueue;
    
    // Others
    private DefaultVNodesAssigner vNodesAssigner;
    private VNodeController vNodeController;
    private TriggerSchedulingLoopFactory triggerSchedulingLoopFactory;
    private CollectorTriggerExecutionPublisher triggerExecutionPublisher;
    private TriggerWorkerJobResultSubscriber triggerWorkerJobResultSubscriber;
    
    @BeforeEach
    void beforeEach() {
        // Mock
        this.triggerWorkerJobResultSubscriber = mock(TriggerWorkerJobResultSubscriber.class);
        when(this.triggerWorkerJobResultSubscriber.subscribe()).thenReturn(Disposable.of(() -> {}));
        
        // Stores
        this.triggerStateStore = new CachedTriggerStateStore(new InMemoryTriggerStateStore(), SCHEDULER_CONFIGURATION);
        this.flowMetaStore = new CachedFlowMetaStore(new InMemoryFlowMetaStore(1, List.of()), SCHEDULER_CONFIGURATION);
        this.serviceLivenessStore = new InMemoryServiceLivenessStore();
        
        // Queues
        this.schedulerEventQueue = new InMemorySchedulerEventQueue();
        this.triggerEventQueue = new InMemoryTriggerEventQueue(SCHEDULER_CONFIGURATION.vnodes());
        
        // Others
        this.vNodesAssigner = new DefaultVNodesAssigner(this.schedulerEventQueue);
        this.vNodesAssigner.start();
        
        this.vNodeController = createVNodeController();
        this.triggerExecutionPublisher = new CollectorTriggerExecutionPublisher();
        this.triggerSchedulingLoopFactory = new TriggerSchedulingLoopFactory(newTriggerScheduler(), newTriggerEventHandler());
       
    }
    
    @AfterEach
    void afterEach() throws IOException {
        this.vNodeController.close();
        this.schedulerEventQueue.close();
        this.triggerEventQueue.close();
        this.vNodesAssigner.stop();
    }
    
    @Test
    void shouldThrowIllegalStateExceptionWhenAlreadyStarted() {
        // GIVEN
        DefaultScheduler scheduler = createDefaultScheduler();
        scheduler.start(2);
        
        // WHEN - THEN
        assertThatThrownBy(() -> scheduler.start(2))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Scheduler already started");
    }

    @Test
    void shouldGetVNodesAssignmentsWhenStartedAndVNodesAreRebalanced() {
        // GIVEN
        DefaultScheduler scheduler1 = createDefaultScheduler();
        DefaultScheduler scheduler2 = createDefaultScheduler();
        
        scheduler1.start(2);
        scheduler2.start(2);
        
        serviceLivenessStore.put(scheduler1);
        serviceLivenessStore.put(scheduler2);
        
        // WHEN - on vNodes re-balancing
        vNodeController.checkServicesAndRebalanceVNodes(); // manually rebalance vNodes
        
        // THEN
        assertThat(scheduler1.currentVNodesAssignment()).isNotEmpty();
        assertThat(scheduler2.currentVNodesAssignment()).isNotEmpty();
        
        // THEN
        assertThat(scheduler1.isActive()).isTrue();
        assertThat(scheduler2.isActive()).isTrue();
        
        // WHEN - on stop
        scheduler1.stop();
        scheduler2.stop();
        
        // THEN
        assertThat(scheduler1.currentVNodesAssignment()).isEmpty();
        assertThat(scheduler2.currentVNodesAssignment()).isEmpty();
        
        assertThat(scheduler1.isActive()).isFalse();
        assertThat(scheduler2.isActive()).isFalse();
    }
    
    private TriggerScheduler newTriggerScheduler() {
        return new TriggerScheduler(
            triggerStateStore,
            flowMetaStore,
            metricRegistry,
            runContextFactory,
            conditionService,
            pluginDefaultService,
            schedulableEvaluator,
            new DefaultSchedulableTriggerFetcher(runContextFactory, triggerStateStore, flowMetaStore, pluginDefaultService),
            triggerWorkerJobPublisher,
            triggerExecutionPublisher,
            SCHEDULER_CONFIGURATION
        );
    }
    
    DefaultScheduler createDefaultScheduler() {
        return new DefaultScheduler(
            triggerSchedulingLoopFactory,
            vNodesAssigner,
            executorsUtils,
            eventPublisher,
            triggerEventQueue,
            triggerWorkerJobResultSubscriber,
            triggerStateStore,
            SchedulerClock.getClock()
        );
    }
    
    VNodeController createVNodeController() {
        return new VNodeController(
            lockService,
            serviceLivenessStore,
            schedulerEventQueue,
            SCHEDULER_CONFIGURATION
        );
    }
    
    TriggerEventHandler newTriggerEventHandler() {
        return new TriggerEventHandler(
            triggerStateStore,
            flowMetaStore,
            triggerExecutionPublisher,
            runContextFactory,
            conditionService
        );
    }
    
    /**
     * An in-memory implementation of {@link ServiceLivenessStore}.
     * <p>
     * Stores {@link ServiceInstance} objects in a concurrent map keyed by their ID.
     * Provides simple filtering by {@link Service.ServiceState}.
     * </p>
     * This class is for testing-purpose only.
     */
    public static class InMemoryServiceLivenessStore implements ServiceLivenessStore {
        
        private final Map<String, ServiceInstance> instances = new ConcurrentHashMap<>();
        
        /**
         * Adds or updates a {@link Service} in the store.
         *
         * @param service the instance to add or update.
         */
        public void put(Service service) {
            put(new ServiceInstance(
                service.getId(),
                service.getType(),
                service.getState(),
                null,
                Instant.now(),
                Instant.now(),
                List.of(),
                null,
                Map.of(),
                service.getMetrics()
            ));
        }
        
        /**
         * Adds or updates a {@link ServiceInstance} in the store.
         *
         * @param instance the instance to add or update.
         */
        public void put(ServiceInstance instance) {
            Objects.requireNonNull(instance, "instance must not be null");
            instances.put(instance.uid(), instance);
        }
        
        /**
         * Removes a {@link ServiceInstance} from the store.
         *
         * @param instanceId the ID of the instance to remove.
         * @return {@code true} if removed, {@code false} if not found.
         */
        public boolean remove(String instanceId) {
            return instances.remove(instanceId) != null;
        }
        
        /** {@inheritDoc} **/
        @Override
        public List<ServiceInstance> findAllInstancesInStates(Set<Service.ServiceState> states) {
            if (states == null || states.isEmpty()) {
                return Collections.emptyList();
            }
            return instances.values().stream()
                .filter(i -> states.contains(i.state()))
                .collect(Collectors.toList());
        }
        
        /** {@inheritDoc} **/
        @Override
        public List<ServiceInstance> findAllInstancesInState(Service.ServiceState state) {
            if (state == null) {
                return Collections.emptyList();
            }
            return instances.values().stream()
                .filter(i -> i.state().equals(state))
                .collect(Collectors.toList());
        }
    }
}