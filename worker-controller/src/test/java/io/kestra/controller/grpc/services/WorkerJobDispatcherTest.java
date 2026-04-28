package io.kestra.controller.grpc.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.kestra.controller.grpc.WorkerJobResponse;
import io.kestra.core.contexts.KestraContext;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.executor.WorkerJobRunningStateStore;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.scheduler.queue.TriggerEventQueue;
import io.kestra.core.server.ClusterEvent;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.utils.Either;

import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.search.Search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WorkerJobDispatcher}.
 */
class WorkerJobDispatcherTest {

    private static final String WORKER_GROUP_A = "group-a";
    private static final String WORKER_GROUP_B = "group-b";

    private KeyedDispatchQueueInterface<WorkerJobEvent> mockQueue;
    private WorkerJobRunningStateStore mockStateStore;
    @SuppressWarnings("unchecked")
    private BroadcastQueueInterface<ExecutionKilled> mockKillQueue = mock(BroadcastQueueInterface.class);
    @SuppressWarnings("unchecked")
    private BroadcastQueueInterface<ClusterEvent> mockClusterEventQueue = mock(BroadcastQueueInterface.class);
    @SuppressWarnings("unchecked")
    private DispatchQueueInterface<WorkerTaskResult> mockResultQueue = mock(DispatchQueueInterface.class);
    private WorkerJobDispatcher dispatcher;
    private TriggerEventQueue mockTriggerEventQueue = mock(TriggerEventQueue.class);
    private MetricRegistry mockMetricRegistry = mock(MetricRegistry.class);

    // Captures for verifying interactions
    private List<MockQueueSubscriber> createdSubscribers;
    private Consumer<Either<ClusterEvent, DeserializationException>> clusterEventConsumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        // Initialize KestraContext for RequestOrResponseHeaderFactory
        KestraContext testContext = mock(KestraContext.class);
        when(testContext.getVersion()).thenReturn("1.0.0-test");
        KestraContext.setContext(testContext);

        mockQueue = mock(KeyedDispatchQueueInterface.class);
        mockStateStore = mock(WorkerJobRunningStateStore.class);
        mockKillQueue = mock(BroadcastQueueInterface.class);
        mockClusterEventQueue = mock(BroadcastQueueInterface.class);
        mockResultQueue = mock(DispatchQueueInterface.class);
        mockTriggerEventQueue = mock(TriggerEventQueue.class);
        mockMetricRegistry = mock(MetricRegistry.class);
        createdSubscribers = new ArrayList<>();

        // Mock workerGroupTags to return proper tag arrays
        when(mockMetricRegistry.workerGroupTags(any())).thenAnswer(invocation -> {
            String group = invocation.getArgument(0);
            return new String[] { "worker_group", group != null ? group : "__default__" };
        });

        // Mock counter to return a no-op counter
        io.micrometer.core.instrument.Counter mockCounter = mock(io.micrometer.core.instrument.Counter.class);
        when(mockMetricRegistry.counter(anyString(), anyString(), any(String[].class))).thenReturn(mockCounter);

        // Mock gauge to return a no-op gauge
        io.micrometer.core.instrument.Gauge mockGauge = mock(io.micrometer.core.instrument.Gauge.class);
        when(mockMetricRegistry.gauge(anyString(), anyString(), any(java.util.function.Supplier.class), any(String[].class))).thenReturn(mockGauge);
        when(mockMetricRegistry.gauge(anyString(), anyString(), any(java.util.function.Supplier.class))).thenReturn(mockGauge);

        // Mock find().tag().gauges() chain for removeGroupGauges
        Search mockSearch = mock(Search.class);
        when(mockMetricRegistry.find(anyString())).thenReturn(mockSearch);
        when(mockSearch.tag(anyString(), anyString())).thenReturn(mockSearch);
        when(mockSearch.gauges()).thenReturn(java.util.Collections.emptyList());

        // Mock kill queue subscriber
        @SuppressWarnings("unchecked")
        QueueSubscriber<ExecutionKilled> killSubscriber = mock(QueueSubscriber.class);
        when(killSubscriber.subscribe(any())).thenReturn(killSubscriber);
        when(mockKillQueue.subscriber()).thenReturn(killSubscriber);

        // Mock cluster event queue subscriber and capture the consumer
        @SuppressWarnings("unchecked")
        QueueSubscriber<ClusterEvent> clusterEventSubscriber = mock(QueueSubscriber.class);
        when(clusterEventSubscriber.subscribe(any())).thenAnswer(invocation ->
        {
            clusterEventConsumer = invocation.getArgument(0);
            return clusterEventSubscriber;
        });
        when(mockClusterEventQueue.subscriber()).thenReturn(clusterEventSubscriber);

        // Create mock subscribers for each group
        when(mockQueue.subscriber(anyString())).thenAnswer(invocation ->
        {
            String group = invocation.getArgument(0);
            MockQueueSubscriber subscriber = new MockQueueSubscriber(group);
            createdSubscribers.add(subscriber);
            return subscriber;
        });

        dispatcher = new WorkerJobDispatcher(mockQueue, mockStateStore, mockKillQueue, mockClusterEventQueue, mockResultQueue, mockTriggerEventQueue, mockMetricRegistry);
    }

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.close();
        }
        KestraContext.setContext(null);
    }

    private WorkerStreamContext<WorkerJobResponse> createWorkerContext(String workerId, String workerGroup, int maxConcurrency) {
        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> mockObserver = mock(StreamObserver.class);
        return new WorkerStreamContext<>(workerId, workerGroup, maxConcurrency, mockObserver);
    }

    private WorkerJobEvent createJobEvent(String jobId, String workerGroup) {
        TaskRun taskRun = mock(TaskRun.class);
        when(taskRun.getExecutionId()).thenReturn("exec-" + jobId);

        WorkerTask mockTask = mock(WorkerTask.class);
        when(mockTask.uid()).thenReturn(jobId);
        when(mockTask.getType()).thenReturn("task");
        when(mockTask.getTaskRun()).thenReturn(taskRun);
        return new WorkerJobEvent(workerGroup, mockTask);
    }

    private MockQueueSubscriber getSubscriberForGroup(String group) {
        return createdSubscribers.stream()
            .filter(s -> s.group.equals(group))
            .findFirst()
            .orElse(null);
    }

    @Nested
    @DisplayName("Worker Registration")
    class WorkerRegistrationTests {

        @Test
        void shouldRegisterWorkerAndCreateSubscription() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);

            // When
            dispatcher.registerWorker(context);

            // Then
            assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(1);
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(1);
            verify(mockQueue).subscriber(WORKER_GROUP_A);
        }

        @Test
        void shouldReuseExistingSubscription() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_A, 10);

            // When
            dispatcher.registerWorker(context1);
            dispatcher.registerWorker(context2);

            // Then
            assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(2);
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(2);
            // Should only create one subscriber
            assertThat(createdSubscribers).hasSize(1);
        }

        @Test
        void shouldCreateSeparateSubscriptionsForDifferentGroups() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_B, 10);

            // When
            dispatcher.registerWorker(context1);
            dispatcher.registerWorker(context2);

            // Then
            assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(2);
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(1);
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_B)).isEqualTo(1);
            assertThat(createdSubscribers).hasSize(2);
        }

        @Test
        void shouldResumeSubscriptionWhenWorkerWithPermitsRegisters() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            context.addPermits(5);

            // When
            dispatcher.registerWorker(context);

            // Then
            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            assertThat(subscriber).isNotNull();
            // Initially paused, then resumed due to permits
            assertThat(subscriber.pauseCount.get()).isGreaterThanOrEqualTo(1);
            assertThat(subscriber.resumeCount.get()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Worker Unregistration")
    class WorkerUnregistrationTests {

        @Test
        void shouldUnregisterWorker() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            // When
            dispatcher.unregisterWorker(context);

            // Then
            assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(0);
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(0);
        }

        @Test
        void shouldHandleUnregisteringUnknownWorker() {
            // Given - a context that was never registered
            WorkerStreamContext<WorkerJobResponse> unknown = createWorkerContext("unknown-worker", WORKER_GROUP_A, 10);

            // When/Then - should not throw
            dispatcher.unregisterWorker(unknown);
            assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(0);
        }

        @Test
        void shouldKeepSubscriptionWhenOtherWorkersRemain() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context1);
            dispatcher.registerWorker(context2);

            // When
            dispatcher.unregisterWorker(context1);

            // Then
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(1);
            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            assertThat(subscriber.closed.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("Permit Handling")
    class PermitHandlingTests {

        @Test
        void shouldSetPermits() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            // When - permits represent total remaining capacity, so they are SET not added
            dispatcher.onPermitsReceived(context, 5);

            // Then
            assertThat(context.getAvailablePermits()).isEqualTo(5);
            assertThat(dispatcher.getTotalPermitsForGroup(WORKER_GROUP_A)).isEqualTo(5);

            // When - setting again should replace, not add
            dispatcher.onPermitsReceived(context, 3);

            // Then - should be 3, not 8
            assertThat(context.getAvailablePermits()).isEqualTo(3);
        }

        @Test
        void shouldResumeSubscriptionOnPermits() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);
            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            int resumesBefore = subscriber.resumeCount.get();

            // When
            dispatcher.onPermitsReceived(context, 3);

            // Then
            assertThat(subscriber.resumeCount.get()).isGreaterThan(resumesBefore);
        }

        @Test
        void shouldNotResumeOnZeroPermits() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);
            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            int resumesBefore = subscriber.resumeCount.get();

            // When
            dispatcher.onPermitsReceived(context, 0);

            // Then
            assertThat(subscriber.resumeCount.get()).isEqualTo(resumesBefore);
        }
    }

    @Nested
    @DisplayName("Job Acknowledgment")
    class JobAcknowledgmentTests {

        @Test
        void shouldAcknowledgeJobs() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            // Simulate in-flight job
            WorkerJob mockJob = mock(WorkerJob.class);
            when(mockJob.uid()).thenReturn("job-1");
            context.trackInFlight("job-1", mockJob);
            assertThat(context.getInFlightCount()).isEqualTo(1);

            // When
            dispatcher.onAcksReceived(context, List.of("job-1"));

            // Then
            assertThat(context.getInFlightCount()).isEqualTo(0);
        }

        @Test
        void shouldHandleAckForUnknownJob() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            // When/Then - should not throw
            dispatcher.onAcksReceived(context, List.of("unknown-job"));
            assertThat(context.getInFlightCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Job Dispatching")
    class JobDispatchingTests {

        @Test
        void shouldDispatchJobToWorkerWithPermits() throws QueueException {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            context.addPermits(5);
            dispatcher.registerWorker(context);

            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

            // When - simulate job from queue
            subscriber.deliverJob(event);

            // Then - verify job was persisted and sent to worker
            verify(mockStateStore).save(any(), any());
            verify(context.getResponseObserver()).onNext(any(WorkerJobResponse.class));
            assertThat(context.getInFlightCount()).isEqualTo(1);
            assertThat(context.getAvailablePermits()).isEqualTo(4);
        }

        @Test
        void shouldDispatchToWorkerWithLowestInFlight() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_A, 10);
            context1.addPermits(5);
            context2.addPermits(5);
            dispatcher.registerWorker(context1);
            dispatcher.registerWorker(context2);

            // Add some in-flight jobs to worker-1
            WorkerJob mockJob = mock(WorkerJob.class);
            context1.trackInFlight("existing-1", mockJob);
            context1.trackInFlight("existing-2", mockJob);

            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

            // When
            subscriber.deliverJob(event);

            // Then - verify dispatch went to worker-2 (lower in-flight count)
            verify(mockStateStore).save(any(), any());
            verify(context2.getResponseObserver()).onNext(any(WorkerJobResponse.class));
            verify(context1.getResponseObserver(), never()).onNext(any(WorkerJobResponse.class));

            // Worker-1 should still have its 2 original in-flight jobs
            assertThat(context1.getInFlightCount()).isEqualTo(2);
            // Worker-2 should now have 1 in-flight job
            assertThat(context2.getInFlightCount()).isEqualTo(1);
        }

        @Test
        void shouldRequeueWhenNoPermits() throws QueueException {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            // No permits added
            dispatcher.registerWorker(context);

            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

            // When
            subscriber.deliverJob(event);

            // Then
            verify(mockQueue).emit(eq(WORKER_GROUP_A), eq(event));
            assertThat(context.getInFlightCount()).isEqualTo(0);
            assertThat(subscriber.isPaused.get()).isTrue();
        }

        @Test
        void shouldPauseAfterLastPermit() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            context.addPermits(1); // Only one permit
            dispatcher.registerWorker(context);

            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
            WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

            // When
            subscriber.deliverJob(event);

            // Then - job was dispatched and subscription paused after last permit consumed
            verify(mockStateStore).save(any(), any());
            verify(context.getResponseObserver()).onNext(any(WorkerJobResponse.class));
            assertThat(context.getAvailablePermits()).isEqualTo(0);
            assertThat(subscriber.isPaused.get()).isTrue();
        }
    }

    @Nested
    @DisplayName("Immediate Disposal")
    class ImmediateDisposalTests {

        @Test
        void shouldDisposeImmediatelyWhenLastWorkerDisconnects() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);
            MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);

            // When
            dispatcher.unregisterWorker(context);

            // Then - subscription should be closed immediately
            assertThat(subscriber.closed.get()).isTrue();
        }

        @Test
        void shouldCreateNewSubscriptionWhenWorkerReconnectsAfterDisposal() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context1);
            dispatcher.unregisterWorker(context1);

            MockQueueSubscriber originalSubscriber = getSubscriberForGroup(WORKER_GROUP_A);
            assertThat(originalSubscriber.closed.get()).isTrue(); // Verify disposed

            // When - new worker connects after disposal
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context2);

            // Then - should create a new subscription
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(1);
            assertThat(createdSubscribers).hasSize(2); // New subscriber created

            // The new subscriber should not be closed
            MockQueueSubscriber newSubscriber = createdSubscribers.get(1);
            assertThat(newSubscriber.group).isEqualTo(WORKER_GROUP_A);
            assertThat(newSubscriber.closed.get()).isFalse();
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperationTests {

        @Test
        void shouldHandleConcurrentRegistrations() throws InterruptedException {
            // Given
            int numWorkers = 20;
            CyclicBarrier barrier = new CyclicBarrier(numWorkers);
            CountDownLatch latch = new CountDownLatch(numWorkers);
            ExecutorService executor = Executors.newFixedThreadPool(numWorkers);

            try {
                // When
                for (int i = 0; i < numWorkers; i++) {
                    final int workerId = i;
                    executor.submit(() ->
                    {
                        try {
                            barrier.await(); // Sync start
                            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-" + workerId, WORKER_GROUP_A, 10);
                            dispatcher.registerWorker(context);
                        } catch (Exception e) {
                            // Ignore
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            // Then
            assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(numWorkers);
            // Should only have one subscriber for the group
            long subscriberCount = createdSubscribers.stream()
                .filter(s -> s.group.equals(WORKER_GROUP_A))
                .filter(s -> !s.closed.get())
                .count();
            assertThat(subscriberCount).isEqualTo(1);
        }

        @Test
        void shouldHandleConcurrentRegisterAndUnregister() throws InterruptedException {
            // Given
            int numIterations = 50;
            CountDownLatch latch = new CountDownLatch(numIterations * 2);
            ExecutorService executor = Executors.newFixedThreadPool(10);
            AtomicInteger errors = new AtomicInteger(0);

            try {
                // When
                for (int i = 0; i < numIterations; i++) {
                    final String workerId = "worker-" + i;
                    final WorkerStreamContext<WorkerJobResponse> context = createWorkerContext(workerId, WORKER_GROUP_A, 10);
                    executor.submit(() ->
                        {
                            try {
                                dispatcher.registerWorker(context);
                            } catch (Exception e) {
                                errors.incrementAndGet();
                            } finally {
                                latch.countDown();
                            }
                        });

                    executor.submit(() ->
                    {
                        try {
                            dispatcher.unregisterWorker(context);
                        } catch (Exception e) {
                            errors.incrementAndGet();
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                latch.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            // Then - no exceptions
            assertThat(errors.get()).isEqualTo(0);
        }

        @Test
        void shouldHandleConcurrentPermitUpdates() throws InterruptedException {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            int numUpdates = 100;
            CountDownLatch latch = new CountDownLatch(numUpdates);
            ExecutorService executor = Executors.newFixedThreadPool(10);

            try {
                // When - permits are SET (not added), so concurrent updates with increasing values
                for (int i = 0; i < numUpdates; i++) {
                    final int permits = i + 1;
                    executor.submit(() ->
                    {
                        try {
                            dispatcher.onPermitsReceived(context, permits);
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                latch.await(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            // Then - with SET semantics, permits should be one of the values sent (no race condition crashes)
            // The final value depends on thread scheduling, but should be between 1 and numUpdates
            assertThat(context.getAvailablePermits()).isBetween(1, numUpdates);
        }
    }

    @Nested
    @DisplayName("Close Behavior")
    class CloseBehaviorTests {

        @Test
        void shouldCloseAllSubscriptionsOnClose() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_B, 10);
            dispatcher.registerWorker(context1);
            dispatcher.registerWorker(context2);

            // When
            dispatcher.close();

            // Then
            assertThat(createdSubscribers).allMatch(s -> s.closed.get());
        }

        @Test
        void shouldHandleMultipleCloseCalls() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            // When/Then - should not throw
            dispatcher.close();
            dispatcher.close();
        }

        @Test
        void shouldRejectRegisterAfterCloseAndNotLeakSubscriber() {
            // Given - dispatcher is closed
            dispatcher.close();
            int subscribersBefore = createdSubscribers.size();
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);

            // When/Then - registerWorker must reject
            assertThatThrownBy(() -> dispatcher.registerWorker(context))
                .isInstanceOf(IllegalStateException.class);

            // Then - if the registration created a subscriber it must have been closed
            // (no subscriber may be left open after close()).
            assertThat(createdSubscribers).allMatch(s -> s.closed.get());
            // Defensive: at most one subscriber may have been created during the doomed call.
            assertThat(createdSubscribers).hasSizeLessThanOrEqualTo(subscribersBefore + 1);
        }
    }

    @Test
    void shouldForwardNonMaintenanceClusterEventsToWorkers() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        ClusterEvent event = new ClusterEvent(
            ClusterEvent.EventType.PLUGINS_SYNC_REQUESTED,
            LocalDateTime.now(),
            "test plugin sync"
        );

        // When
        clusterEventConsumer.accept(Either.left(event));

        // Then — verify the event was sent to the worker's stream observer
        verify(context.getResponseObserver()).onNext(any(WorkerJobResponse.class));
    }

    @Test
    void shouldFilterOutMaintenanceEnterEvents() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        ClusterEvent maintenanceEvent = new ClusterEvent(
            ClusterEvent.EventType.MAINTENANCE_ENTER,
            LocalDateTime.now(),
            "entering maintenance"
        );

        // When
        clusterEventConsumer.accept(Either.left(maintenanceEvent));

        // Then — maintenance events should NOT be forwarded (they use heartbeat path)
        verify(context.getResponseObserver(), never()).onNext(any(WorkerJobResponse.class));
    }

    @Test
    void shouldFilterOutMaintenanceExitEvents() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        ClusterEvent maintenanceEvent = new ClusterEvent(
            ClusterEvent.EventType.MAINTENANCE_EXIT,
            LocalDateTime.now(),
            "exiting maintenance"
        );

        // When
        clusterEventConsumer.accept(Either.left(maintenanceEvent));

        // Then — maintenance events should NOT be forwarded (they use heartbeat path)
        verify(context.getResponseObserver(), never()).onNext(any(WorkerJobResponse.class));
    }

    @Test
    void shouldFilterOutKillSwitchSyncEvents() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        ClusterEvent killSwitchEvent = new ClusterEvent(
            ClusterEvent.EventType.KILL_SWITCH_SYNC_REQUESTED,
            LocalDateTime.now(),
            "kill switch sync"
        );

        // When
        clusterEventConsumer.accept(Either.left(killSwitchEvent));

        // Then — executor-only events should NOT be forwarded to workers
        verify(context.getResponseObserver(), never()).onNext(any(WorkerJobResponse.class));
    }

    @Test
    void shouldBroadcastClusterEventsToAllConnectedWorkers() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context1 = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        WorkerStreamContext<WorkerJobResponse> context2 = createWorkerContext("worker-2", WORKER_GROUP_B, 10);
        dispatcher.registerWorker(context1);
        dispatcher.registerWorker(context2);

        ClusterEvent event = new ClusterEvent(
            ClusterEvent.EventType.PLUGINS_SYNC_REQUESTED,
            LocalDateTime.now(),
            "plugin sync for all"
        );

        // When
        clusterEventConsumer.accept(Either.left(event));

        // Then — both workers should receive the event
        verify(context1.getResponseObserver()).onNext(any(WorkerJobResponse.class));
        verify(context2.getResponseObserver()).onNext(any(WorkerJobResponse.class));
    }

    @Test
    void shouldRegisterGlobalGaugesOnConstruction() {
        // Then - global gauges should have been registered during setUp()
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_TOTAL_ACTIVE_WORKER_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_TOTAL_ACTIVE_WORKER_COUNT_DESCRIPTION),
            any(java.util.function.Supplier.class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_TOTAL_AVAILABLE_PERMITS_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_TOTAL_AVAILABLE_PERMITS_COUNT_DESCRIPTION),
            any(java.util.function.Supplier.class)
        );
    }

    @Test
    void shouldRegisterPerGroupGaugesOnFirstWorkerRegistration() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);

        // When
        dispatcher.registerWorker(context);

        // Then
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_ACTIVE_WORKER_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_ACTIVE_WORKER_COUNT_DESCRIPTION),
            any(java.util.function.Supplier.class),
            any(String[].class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_AVAILABLE_PERMITS_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_AVAILABLE_PERMITS_COUNT_DESCRIPTION),
            any(java.util.function.Supplier.class),
            any(String[].class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_INFLIGHT_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_INFLIGHT_COUNT_DESCRIPTION),
            any(java.util.function.Supplier.class),
            any(String[].class)
        );
    }

    @Test
    void shouldIncrementWorkerRegisteredCounterOnRegister() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);

        // When
        dispatcher.registerWorker(context);

        // Then
        verify(mockMetricRegistry).counter(
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_COUNT_DESCRIPTION),
            any(String[].class)
        );
    }

    @Test
    void shouldIncrementWorkerUnregisteredCounterOnUnregister() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        // When
        dispatcher.unregisterWorker(context);

        // Then
        verify(mockMetricRegistry).counter(
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_COUNT_DESCRIPTION),
            any(String[].class)
        );
    }

    @Test
    void shouldIncrementJobDispatchedCounterOnDispatch() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        context.addPermits(5);
        dispatcher.registerWorker(context);

        MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
        WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

        // When
        subscriber.deliverJob(event);

        // Then
        verify(mockMetricRegistry).counter(
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_COUNT_DESCRIPTION),
            any(String[].class)
        );
    }

    @Test
    void shouldIncrementJobRequeuedCounterWhenNoPermits() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        // No permits
        dispatcher.registerWorker(context);

        MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
        WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

        // When
        subscriber.deliverJob(event);

        // Then
        verify(mockMetricRegistry).counter(
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_COUNT),
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_COUNT_DESCRIPTION),
            any(String[].class)
        );
    }

    @Test
    void shouldRemovePerGroupGaugesWhenLastWorkerDisconnects() {
        // Given
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(context);

        // When
        dispatcher.unregisterWorker(context);

        // Then - find should be called to locate gauges for removal
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_ACTIVE_WORKER_COUNT);
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_AVAILABLE_PERMITS_COUNT);
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_INFLIGHT_COUNT);
    }

    @Test
    void shouldNotRemoveNewerRegistrationWhenStaleStreamCancelLateFires() {
        // Reproduces the bug where a delayed onError/onCancel for an old stream
        // (e.g., after an HTTP/2 GOAWAY max_age reconnect) wipes out a fresh
        // registration the worker has already established for the same workerId.
        // The controller then has no worker state and jobs get re-queued while
        // the worker is still happily connected on the new stream.

        // Given - a worker is registered with stream A
        WorkerStreamContext<WorkerJobResponse> streamA = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(streamA);

        // And - stream A has been unregistered (e.g., GOAWAY received)
        dispatcher.unregisterWorker(streamA);

        // And - the worker reconnects as stream B for the same workerId
        WorkerStreamContext<WorkerJobResponse> streamB = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
        dispatcher.registerWorker(streamB);

        // When - a late onError/onCancel callback fires for the stale stream A
        dispatcher.unregisterWorker(streamA);

        // Then - stream B's registration must remain intact
        assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(1);
        assertThat(dispatcher.getActiveWorkerCount(WORKER_GROUP_A)).isEqualTo(1);
        MockQueueSubscriber activeSubscriber = createdSubscribers.stream()
            .filter(s -> s.group.equals(WORKER_GROUP_A))
            .filter(s -> !s.closed.get())
            .findFirst()
            .orElse(null);
        assertThat(activeSubscriber).isNotNull();
    }

    /**
     * Mock implementation of QueueSubscriber for testing.
     */
    static class MockQueueSubscriber implements QueueSubscriber<WorkerJobEvent> {
        final String group;
        final AtomicBoolean isPaused = new AtomicBoolean(false);
        final AtomicBoolean closed = new AtomicBoolean(false);
        final AtomicInteger pauseCount = new AtomicInteger(0);
        final AtomicInteger resumeCount = new AtomicInteger(0);
        final AtomicReference<Consumer<Either<WorkerJobEvent, DeserializationException>>> consumer = new AtomicReference<>();

        MockQueueSubscriber(String group) {
            this.group = group;
        }

        @Override
        public QueueSubscriber<WorkerJobEvent> subscribe(Consumer<Either<WorkerJobEvent, DeserializationException>> consumer) {
            this.consumer.set(consumer);
            return this;
        }

        @Override
        public void pause() {
            isPaused.set(true);
            pauseCount.incrementAndGet();
        }

        @Override
        public void resume() {
            isPaused.set(false);
            resumeCount.incrementAndGet();
        }

        @Override
        public void close() {
            closed.set(true);
        }

        /**
         * Simulates a job being delivered from the queue.
         */
        void deliverJob(WorkerJobEvent event) {
            Consumer<Either<WorkerJobEvent, DeserializationException>> c = consumer.get();
            if (c != null) {
                c.accept(Either.left(event));
            }
        }
    }

}
