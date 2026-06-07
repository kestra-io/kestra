package io.kestra.controller.grpc.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import io.kestra.core.metrics.MetricRegistry;
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
import io.kestra.core.utils.Either;
import io.kestra.core.worker.WorkerGroups;
import io.kestra.core.worker.WorkerQueues;

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

        // Mock workerQueueTags to return well-formed tag arrays.
        when(mockMetricRegistry.workerQueueTags(any())).thenAnswer(invocation ->
        {
            String workerQueueId = invocation.getArgument(0);
            return new String[] { MetricRegistry.TAG_WORKER_QUEUE, WorkerQueues.normalize(workerQueueId) };
        });
        // Mock workerGroupAndQueueTags for per-worker counters (registered, unregistered, dispatched, dispatch.failed).
        when(mockMetricRegistry.workerGroupAndQueueTags(any(), any())).thenAnswer(invocation ->
        {
            String workerGroupId = invocation.getArgument(0);
            String workerQueueId = invocation.getArgument(1);
            return new String[] {
                MetricRegistry.TAG_WORKER_QUEUE, WorkerQueues.normalize(workerQueueId),
                MetricRegistry.TAG_WORKER_GROUP, WorkerGroups.normalize(workerGroupId)
            };
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

        // Create mock subscribers for each group (including null for default group)
        when(mockQueue.subscriber(any())).thenAnswer(invocation ->
        {
            String group = invocation.getArgument(0);
            MockQueueSubscriber subscriber = new MockQueueSubscriber(group);
            createdSubscribers.add(subscriber);
            return subscriber;
        });

        dispatcher = buildDispatcher(List.of());
    }

    private WorkerJobDispatcher buildDispatcher(List<WorkerLifecycleListener> listeners) {
        return new WorkerJobDispatcher(
            mockQueue, mockStateStore, mockKillQueue, mockClusterEventQueue, mockResultQueue, mockTriggerEventQueue, mockMetricRegistry, mock(MetadataChangeListener.class), new WorkerQueueResolver.Default(), listeners
        );
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
        String queueId = workerGroup == null || workerGroup.isEmpty()
            ? io.kestra.core.worker.WorkerQueues.DEFAULT_ID
            : workerGroup;
        return new WorkerStreamContext<>(
            workerId, "",
            java.util.List.of(
                new io.kestra.core.worker.QueueSubscription(queueId, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
            ),
            maxConcurrency, mockObserver
        );
    }

    private WorkerStreamContext<WorkerJobResponse> createWorkerContext(String workerId, String workerGroupId, String workerQueueId, int maxConcurrency) {
        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> mockObserver = mock(StreamObserver.class);
        String queueId = workerQueueId == null || workerQueueId.isEmpty()
            ? io.kestra.core.worker.WorkerQueues.DEFAULT_ID
            : workerQueueId;
        return new WorkerStreamContext<>(
            workerId, workerGroupId,
            java.util.List.of(
                new io.kestra.core.worker.QueueSubscription(queueId, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
            ),
            maxConcurrency, mockObserver
        );
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
            .filter(s -> java.util.Objects.equals(s.group, group))
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

        @Test
        void shouldEmitWorkerGroupAndQueueTagsWhenWorkerRegisters() {
            // Given
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", "group-a", "queue-1", 10);

            // When
            dispatcher.registerWorker(context);

            // Then: per-worker counter must carry both worker_group and worker_queue tags.
            verify(mockMetricRegistry).workerGroupAndQueueTags(eq("group-a"), eq("queue-1"));
            // And: queue-scoped gauges must carry only worker_queue tag.
            verify(mockMetricRegistry, atLeastOnce()).workerQueueTags(eq("queue-1"));
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
            assertThat(dispatcher.getTotalPermitsForWorkerQueue(WORKER_GROUP_A)).isEqualTo(5);

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
    @DisplayName("Job Completion")
    class JobCompletionTests {

        @Test
        void shouldRemoveInFlightAndReleaseBucketOnCompletion() {
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            String bucket = context.tryReserveBucket(WORKER_GROUP_A);
            assertThat(bucket).isEqualTo(WorkerStreamContext.PendingJob.SHARED);
            WorkerJob mockJob = mock(WorkerJob.class);
            when(mockJob.uid()).thenReturn("job-1");
            context.trackInFlight("job-1", mockJob, bucket);
            assertThat(context.getInFlightCount()).isEqualTo(1);
            assertThat(context.sharedFree()).isEqualTo(9);

            dispatcher.onCompletionsReceived(context, List.of("job-1"));

            assertThat(context.getInFlightCount()).isEqualTo(0);
            assertThat(context.sharedFree()).isEqualTo(10);
        }

        @Test
        void shouldHandleCompletionForUnknownJob() {
            // Unknown ids must be a no-op — supports the controller-restart scenario
            // where results for jobs dispatched on a prior stream arrive on a new
            // controller that doesn't know them.
            WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 10);
            dispatcher.registerWorker(context);

            dispatcher.onCompletionsReceived(context, List.of("unknown-job"));
            assertThat(context.getInFlightCount()).isEqualTo(0);
            assertThat(context.sharedFree()).isEqualTo(10);
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
            context1.trackInFlight("existing-1", mockJob, WorkerStreamContext.PendingJob.SHARED);
            context1.trackInFlight("existing-2", mockJob, WorkerStreamContext.PendingJob.SHARED);

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
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_ALL),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_ALL_DESCRIPTION),
            any(java.util.function.Supplier.class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_ALL),
            eq(MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_ALL_DESCRIPTION),
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
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE_DESCRIPTION),
            any(java.util.function.Supplier.class),
            any(String[].class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE),
            eq(MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE_DESCRIPTION),
            any(java.util.function.Supplier.class),
            any(String[].class)
        );
        verify(mockMetricRegistry).gauge(
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT),
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT_DESCRIPTION),
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
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_REGISTERED_TOTAL_DESCRIPTION),
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
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL),
            eq(MetricRegistry.METRIC_CONTROLLER_WORKER_UNREGISTERED_TOTAL_DESCRIPTION),
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
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_TOTAL),
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_DISPATCHED_TOTAL_DESCRIPTION),
            any(String[].class)
        );
    }

    @Test
    void shouldIncrementJobRequeuedCounterWhenNoPermits() {
        // Given - worker with zero maxConcurrency has no bucket capacity
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 0);
        dispatcher.registerWorker(context);

        MockQueueSubscriber subscriber = getSubscriberForGroup(WORKER_GROUP_A);
        WorkerJobEvent event = createJobEvent("job-1", WORKER_GROUP_A);

        // When
        subscriber.deliverJob(event);

        // Then
        verify(mockMetricRegistry).counter(
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_TOTAL),
            eq(MetricRegistry.METRIC_CONTROLLER_JOB_REQUEUED_TOTAL_DESCRIPTION),
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
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_WORKER_ACTIVE);
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_PERMITS_AVAILABLE);
        verify(mockMetricRegistry, atLeastOnce()).find(MetricRegistry.METRIC_CONTROLLER_JOB_INFLIGHT);
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

    // --- Multi-Group Tests ---

    private WorkerStreamContext<WorkerJobResponse> createMultiGroupWorkerContext(
        String workerId, String tokenId, List<io.kestra.core.worker.QueueSubscription> subscriptions, int maxConcurrency) {
        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> mockObserver = mock(StreamObserver.class);
        return new WorkerStreamContext<>(workerId, tokenId, subscriptions, maxConcurrency, mockObserver);
    }

    @Test
    void shouldRegisterWorkerInAllSubscribedGroups() {
        // Given
        var subscriptions = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", io.kestra.core.worker.QueueSubscription.NO_RESERVATION),
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "token-1", subscriptions, 3);
        context.setPermits(3);

        // When
        dispatcher.registerWorker(context);

        // Then
        assertThat(dispatcher.getActiveWorkerCount("gpu")).isEqualTo(1);
        // Default queue is tracked under the normalized empty-string key.
        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(1);
        assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(1); // 1 physical worker
    }

    @Test
    void shouldDispatchToLeastLoadedWorkerForSameGroup() {
        // Given - two workers subscribed to the same group, worker-1 already has in-flight jobs
        var subs1 = List.of(new io.kestra.core.worker.QueueSubscription("group-a", io.kestra.core.worker.QueueSubscription.NO_RESERVATION));
        var subs2 = List.of(new io.kestra.core.worker.QueueSubscription("group-a", io.kestra.core.worker.QueueSubscription.NO_RESERVATION));

        WorkerStreamContext<WorkerJobResponse> ctx1 = createMultiGroupWorkerContext("worker-1", "", subs1, 10);
        WorkerStreamContext<WorkerJobResponse> ctx2 = createMultiGroupWorkerContext("worker-2", "", subs2, 10);
        ctx1.setPermits(5);
        ctx2.setPermits(5);

        // Give worker-1 some in-flight jobs so worker-2 is least-loaded
        ctx1.trackInFlight("existing-1", mock(WorkerJob.class), WorkerStreamContext.PendingJob.SHARED);
        ctx1.trackInFlight("existing-2", mock(WorkerJob.class), WorkerStreamContext.PendingJob.SHARED);

        dispatcher.registerWorker(ctx1);
        dispatcher.registerWorker(ctx2);

        // When - deliver a job
        MockQueueSubscriber sub = getSubscriberForGroup(WORKER_GROUP_A);
        assertThat(sub).isNotNull();
        sub.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));

        // Then - worker-2 (least-loaded, 0 in-flight) should get the job
        assertThat(ctx2.getInFlightCount()).isEqualTo(1);
        assertThat(ctx1.getInFlightCount()).isEqualTo(2); // still only the pre-existing jobs
    }

    @Test
    void shouldFallbackToOtherWorkerWhenFirstIsSaturated() {
        // Given - worker-1 has maxConcurrency=1 (bucket exhausted after 1 job), worker-2 has room
        var subs1 = List.of(new io.kestra.core.worker.QueueSubscription("group-a", io.kestra.core.worker.QueueSubscription.NO_RESERVATION));
        var subs2 = List.of(new io.kestra.core.worker.QueueSubscription("group-a", io.kestra.core.worker.QueueSubscription.NO_RESERVATION));

        WorkerStreamContext<WorkerJobResponse> ctx1 = createMultiGroupWorkerContext("worker-1", "", subs1, 1);
        WorkerStreamContext<WorkerJobResponse> ctx2 = createMultiGroupWorkerContext("worker-2", "", subs2, 10);
        ctx1.setPermits(1);
        ctx2.setPermits(5);

        dispatcher.registerWorker(ctx1);
        dispatcher.registerWorker(ctx2);

        MockQueueSubscriber sub = getSubscriberForGroup(WORKER_GROUP_A);

        // When - first job goes to worker-1 (least-loaded, both at 0 in-flight, but worker-1 has lower maxConcurrency)
        sub.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));
        // One of them gets the job (non-deterministic tie at 0 in-flight)
        int total = ctx1.getInFlightCount() + ctx2.getInFlightCount();
        assertThat(total).isEqualTo(1);

        // Second job: whichever worker got the first job is now more loaded or saturated
        sub.deliverJob(createJobEvent("job-2", WORKER_GROUP_A));
        // Both jobs should be distributed
        assertThat(ctx1.getInFlightCount() + ctx2.getInFlightCount()).isEqualTo(2);
    }

    @Test
    void shouldResumeSubscriptionWhenCompletionsReleaseCapacityWithoutPermitUpdate() {
        // Given - worker fully loaded: every permit consumed and every bucket slot
        // taken, so the subscription has paused. The worker then signals job
        // completion without a permit update (e.g., between the periodic permit
        // refreshes). The freed buckets must be enough to resume the subscription
        // on their own.
        WorkerStreamContext<WorkerJobResponse> context = createWorkerContext("worker-1", WORKER_GROUP_A, 2);
        context.setPermits(2);
        dispatcher.registerWorker(context);

        MockQueueSubscriber sub = getSubscriberForGroup(WORKER_GROUP_A);

        // Fill both slots — this exhausts permits and buckets, pausing the subscription.
        sub.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));
        sub.deliverJob(createJobEvent("job-2", WORKER_GROUP_A));
        assertThat(context.getAvailablePermits()).isEqualTo(0);
        assertThat(context.getInFlightCount()).isEqualTo(2);
        assertThat(sub.isPaused.get()).isTrue();

        // Simulate the worker reporting fresh permits while the subscription is paused.
        // (Without bucket releases, this is what would normally drive resume.)
        // Here we test that completions alone — followed by no permit update — also resume.
        context.setPermits(2);

        // When - worker signals completion of both jobs without a separate permit update.
        dispatcher.onCompletionsReceived(context, List.of("job-1", "job-2"));

        // Then - bucket release alone must trigger resume.
        assertThat(context.getInFlightCount()).isEqualTo(0);
        assertThat(sub.isPaused.get()).isFalse();
    }

    // Reservation-specific dispatcher tests (bucket math, percentage reconfig)
    // live in worker-controller-ee/WorkerJobDispatcherReservationTest.

    @Test
    void shouldFallbackWhenPreferredWorkerLosesPermitCasRace() {
        // Given - two workers eligible for the same Worker Queue. The preferred
        // (least-loaded) worker simulates losing the permit CAS race to a
        // concurrent dispatch on another Worker Queue. The dispatcher must fall
        // through to the next candidate instead of pausing the subscription.
        var subs = List.of(new io.kestra.core.worker.QueueSubscription(
            WORKER_GROUP_A, io.kestra.core.worker.QueueSubscription.NO_RESERVATION));

        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> obs1 = mock(StreamObserver.class);
        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> obs2 = mock(StreamObserver.class);

        WorkerStreamContext<WorkerJobResponse> ctx1 = new WorkerStreamContext<>(
            "worker-1", "", subs, 10, obs1) {
            @Override
            public boolean tryConsumePermit() {
                return false; // simulate concurrent steal between filter and CAS
            }
        };
        WorkerStreamContext<WorkerJobResponse> ctx2 = createMultiGroupWorkerContext(
            "worker-2", "", subs, 10);

        ctx1.setPermits(5);
        ctx2.setPermits(5);
        // Make worker-1 the preferred (least-loaded) candidate.
        ctx2.trackInFlight("existing", mock(WorkerJob.class), WorkerStreamContext.PendingJob.SHARED);

        dispatcher.registerWorker(ctx1);
        dispatcher.registerWorker(ctx2);

        // When
        MockQueueSubscriber sub = getSubscriberForGroup(WORKER_GROUP_A);
        sub.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));

        // Then - dispatch fell through to worker-2 instead of pausing.
        assertThat(ctx1.getInFlightCount()).isEqualTo(0);
        assertThat(ctx2.getInFlightCount()).isEqualTo(2); // existing + new dispatch
        assertThat(sub.isPaused.get()).isFalse();
    }

    @Test
    void shouldFallbackWhenPreferredWorkerLosesBucketCasRace() {
        // Given - the preferred worker simulates losing the bucket CAS race
        // (another dispatch grabbed the last bucket slot between filter and
        // reserve). The dispatcher must restore the consumed permit and fall
        // through to the next candidate.
        var subs = List.of(new io.kestra.core.worker.QueueSubscription(
            WORKER_GROUP_A, io.kestra.core.worker.QueueSubscription.NO_RESERVATION));

        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> obs1 = mock(StreamObserver.class);
        @SuppressWarnings("unchecked")
        StreamObserver<WorkerJobResponse> obs2 = mock(StreamObserver.class);

        WorkerStreamContext<WorkerJobResponse> ctx1 = new WorkerStreamContext<>(
            "worker-1", "", subs, 10, obs1) {
            @Override
            public String tryReserveBucket(String workerQueueId) {
                return null; // simulate concurrent reservation took the last slot
            }
        };
        WorkerStreamContext<WorkerJobResponse> ctx2 = createMultiGroupWorkerContext(
            "worker-2", "", subs, 10);

        ctx1.setPermits(5);
        ctx2.setPermits(5);
        ctx2.trackInFlight("existing", mock(WorkerJob.class), WorkerStreamContext.PendingJob.SHARED);

        dispatcher.registerWorker(ctx1);
        dispatcher.registerWorker(ctx2);

        // When
        MockQueueSubscriber sub = getSubscriberForGroup(WORKER_GROUP_A);
        sub.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));

        // Then - dispatch fell through; permit on worker-1 was restored.
        assertThat(ctx1.getInFlightCount()).isEqualTo(0);
        assertThat(ctx1.getAvailablePermits()).isEqualTo(5);
        assertThat(ctx2.getInFlightCount()).isEqualTo(2);
        assertThat(sub.isPaused.get()).isFalse();
    }

    @Test
    void shouldUnregisterWorkerFromAllSubscribedGroups() {
        // Given
        var subscriptions = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", io.kestra.core.worker.QueueSubscription.NO_RESERVATION),
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "", subscriptions, 3);
        context.setPermits(3);
        dispatcher.registerWorker(context);

        // When
        dispatcher.unregisterWorker(context);

        // Then
        assertThat(dispatcher.getActiveWorkerCount("gpu")).isEqualTo(0);
        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(0);
        assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(0);
    }

    @Test
    void shouldResumeAllSubscribedGroupsWhenPermitsReceived() {
        // Given - worker subscribed to two queues with no reservation (all capacity is shared)
        var subscriptions = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", io.kestra.core.worker.QueueSubscription.NO_RESERVATION),
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "", subscriptions, 10);
        context.setPermits(0); // start with no permits
        dispatcher.registerWorker(context);

        MockQueueSubscriber gpuSub = getSubscriberForGroup("gpu");
        // The dispatcher passes null to the queue subscriber for the default queue
        // (normalized empty-string id), so look it up under null here.
        MockQueueSubscriber defaultSub = getSubscriberForGroup(null);
        assertThat(gpuSub).isNotNull();
        assertThat(defaultSub).isNotNull();

        // Both should be paused (no permits initially)
        assertThat(gpuSub.isPaused.get()).isTrue();
        assertThat(defaultSub.isPaused.get()).isTrue();

        // When - worker gets permits
        dispatcher.onPermitsReceived(context, 10);

        // Then - both groups should be resumed (shared bucket has capacity for both)
        assertThat(gpuSub.isPaused.get()).isFalse();
        assertThat(defaultSub.isPaused.get()).isFalse();
    }

    @Test
    void shouldMoveWorkerBetweenGroupsWhenReRegistered() {
        // Given - worker initially in gpu + default
        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", io.kestra.core.worker.QueueSubscription.NO_RESERVATION),
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "token-1", initialSubs, 3);
        context.setPermits(3);
        dispatcher.registerWorker(context);

        assertThat(dispatcher.getActiveWorkerCount("gpu")).isEqualTo(1);
        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(1);

        // When - re-register with different groups (gpu + batch, no longer default)
        var newSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", io.kestra.core.worker.QueueSubscription.NO_RESERVATION),
            new io.kestra.core.worker.QueueSubscription("batch", io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        dispatcher.reRegisterWorker("worker-1", newSubs);

        // Then
        assertThat(dispatcher.getActiveWorkerCount("gpu")).isEqualTo(1); // still in gpu
        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(0); // removed from default
        assertThat(dispatcher.getActiveWorkerCount("batch")).isEqualTo(1); // added to batch
        assertThat(context.getAvailablePermits()).isEqualTo(3); // permits preserved
    }

    @Test
    void shouldRecreateDefaultSubscriberWhenDisabledThenReEnabled() {
        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "default", initialSubs, 3);
        context.setPermits(3);
        dispatcher.registerWorker(context);

        MockQueueSubscriber firstSubscriber = getSubscriberForGroup(null);
        assertThat(firstSubscriber).isNotNull();
        assertThat(firstSubscriber.isPaused.get()).isFalse();
        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(1);

        dispatcher.reRegisterWorker("worker-1", List.of());

        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(0);
        assertThat(firstSubscriber.closed.get()).isTrue();
        assertThat(context.subscribedWorkerQueueIds()).isEmpty();

        dispatcher.reRegisterWorker("worker-1", initialSubs);

        assertThat(dispatcher.getActiveWorkerCount("")).isEqualTo(1);
        assertThat(context.subscribedWorkerQueueIds()).containsExactly("");

        MockQueueSubscriber secondSubscriber = createdSubscribers.stream()
            .filter(s -> s.group == null)
            .filter(s -> !s.closed.get())
            .findFirst()
            .orElse(null);
        assertThat(secondSubscriber).isNotNull();
        assertThat(secondSubscriber).isNotSameAs(firstSubscriber);
        assertThat(secondSubscriber.isPaused.get()).isFalse();
    }

    @Test
    void shouldTrackWorkersByWorkerGroupId() {
        // Given
        var subs = List.of(new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION));
        WorkerStreamContext<WorkerJobResponse> ctx1 = createMultiGroupWorkerContext("worker-1", "group-A", subs, 3);
        WorkerStreamContext<WorkerJobResponse> ctx2 = createMultiGroupWorkerContext("worker-2", "group-A", subs, 3);
        ctx1.setPermits(1);
        ctx2.setPermits(1);

        // When
        dispatcher.registerWorker(ctx1);
        dispatcher.registerWorker(ctx2);

        // Then
        assertThat(dispatcher.getWorkerIdsByWorkerGroup("group-A")).containsExactlyInAnyOrder("worker-1", "worker-2");
        assertThat(dispatcher.getWorkerIdsByWorkerGroup("group-B")).isEmpty();

        // When - unregister one
        dispatcher.unregisterWorker(ctx1);
        assertThat(dispatcher.getWorkerIdsByWorkerGroup("group-A")).containsExactly("worker-2");
    }

    // --- Capacity Share & Reconfiguration Tests ---

    @Test
    void shouldResumeSubscriptionWhenPercentagesChangeWithoutGroupChange() {
        // Given - worker subscribed to A (50%) and B (25%), maxConcurrency=100
        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("group-a", 50),
            new io.kestra.core.worker.QueueSubscription("group-b", 25)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "group-1", initialSubs, 100);
        context.setPermits(0); // exhausted — subscriptions will be paused
        dispatcher.registerWorker(context);

        MockQueueSubscriber subA = getSubscriberForGroup(WORKER_GROUP_A);
        MockQueueSubscriber subB = getSubscriberForGroup(WORKER_GROUP_B);
        assertThat(subA.isPaused.get()).isTrue();
        assertThat(subB.isPaused.get()).isTrue();

        // When — give the worker permits (simulating a job completing) and then
        // reconfigure to A=10%, B=10% — this frees shared capacity
        context.addPermits(5);
        var newSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("group-a", 10),
            new io.kestra.core.worker.QueueSubscription("group-b", 10)
        );
        dispatcher.reRegisterWorker("worker-1", newSubs);

        // Then — both subscriptions should be resumed since permits > 0 and shared capacity is available
        assertThat(subA.isPaused.get()).isFalse();
        assertThat(subB.isPaused.get()).isFalse();
    }

    // shouldNotDispatchToOverCommittedGuaranteedBucket moved to EE
    // (worker-controller-ee/WorkerJobDispatcherReservationTest).

    @Test
    void shouldResumeSubscriptionAfterWorkerGroupSyncClusterEvent() {
        // Given - worker in group-1, subscribed to default queue
        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription(io.kestra.core.worker.WorkerQueues.DEFAULT_ID, io.kestra.core.worker.QueueSubscription.NO_RESERVATION)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "group-1", initialSubs, 10);
        context.setPermits(5);
        dispatcher.registerWorker(context);

        // Verify initial state
        assertThat(dispatcher.getWorkerIdsByWorkerGroup("group-1")).contains("worker-1");

        // When — fire a WORKER_GROUP_SYNC_REQUESTED event
        // (The dispatcher calls workerQueueResolver.resolve() which returns the default subscription).
        ClusterEvent syncEvent = new ClusterEvent(
            ClusterEvent.EventType.WORKER_GROUP_SYNC_REQUESTED,
            LocalDateTime.now(),
            "group-1"
        );
        clusterEventConsumer.accept(Either.left(syncEvent));

        // Then — worker should still be registered and subscriptions intact
        assertThat(dispatcher.getActiveWorkerCount()).isEqualTo(1);
    }

    // shouldPreservePermitsDuringReRegistrationWithPercentageChange moved to EE
    // (worker-controller-ee/WorkerJobDispatcherReservationTest).

    @Test
    void shouldPauseGroupAfterReRegistrationWhenNoCapacity() {
        // Given - worker fully loaded on group-a (maxConcurrency=2, 100% reserved)
        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("group-a", 100)
        );
        WorkerStreamContext<WorkerJobResponse> context = createMultiGroupWorkerContext("worker-1", "", initialSubs, 2);
        context.setPermits(2);
        dispatcher.registerWorker(context);

        MockQueueSubscriber subA = getSubscriberForGroup(WORKER_GROUP_A);

        // Fill both slots
        subA.deliverJob(createJobEvent("job-1", WORKER_GROUP_A));
        subA.deliverJob(createJobEvent("job-2", WORKER_GROUP_A));
        assertThat(context.getAvailablePermits()).isEqualTo(0);
        assertThat(subA.isPaused.get()).isTrue(); // paused: no permits

        // When — re-register with same group, different percentage (but still no permits)
        var newSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("group-a", 50)
        );
        dispatcher.reRegisterWorker("worker-1", newSubs);

        // Then — should remain paused (still no permits)
        assertThat(subA.isPaused.get()).isTrue();
    }

    @Test
    void shouldFireLifecycleListenerWhenWorkerRegistersAndUnregisters() {
        // Given
        WorkerLifecycleListener listener = mock(WorkerLifecycleListener.class);
        dispatcher.close();
        dispatcher = buildDispatcher(List.of(listener));

        WorkerStreamContext<WorkerJobResponse> ctx = createWorkerContext("w1", "group-a", "gpu", 10);
        ctx.setPermits(10);

        // When
        dispatcher.registerWorker(ctx);

        // Then
        verify(listener).onWorkerRegistered(ctx);

        // When
        dispatcher.unregisterWorker(ctx);

        // Then
        verify(listener).onWorkerUnregistered(ctx);
    }

    @Test
    void shouldInitLifecycleListenerAtConstructionTime() {
        // Given
        WorkerLifecycleListener listener = mock(WorkerLifecycleListener.class);
        dispatcher.close();

        // When
        dispatcher = buildDispatcher(List.of(listener));

        // Then — listener is initialized with the dispatcher reference
        verify(listener).init(dispatcher);
    }

    @Test
    void shouldFireSubscriptionsChangedWhenWorkerReRegisters() {
        // Given — dispatcher wired with the listener up front
        WorkerLifecycleListener listener = mock(WorkerLifecycleListener.class);
        dispatcher.close();
        dispatcher = buildDispatcher(List.of(listener));

        var initialSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", 50),
            new io.kestra.core.worker.QueueSubscription("cpu", 50)
        );
        WorkerStreamContext<WorkerJobResponse> ctx = createMultiGroupWorkerContext("w1", "group-a", initialSubs, 10);
        ctx.setPermits(10);
        dispatcher.registerWorker(ctx);
        verify(listener).onWorkerRegistered(ctx);

        // When — drop "cpu", add "tpu"
        var newSubs = List.of(
            new io.kestra.core.worker.QueueSubscription("gpu", 50),
            new io.kestra.core.worker.QueueSubscription("tpu", 50)
        );
        dispatcher.reRegisterWorker("w1", newSubs);

        // Then
        verify(listener).onWorkerSubscriptionsChanged(
            eq(ctx),
            eq(Set.of("tpu")),
            eq(Set.of("cpu"))
        );
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
        public boolean isPaused() {
            return isPaused.get();
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

    @Test
    @DisplayName("broadcastToAllWorkers fans out the given event to every connected worker")
    void broadcastToAllWorkers_sendsToAllConnectedWorkers() {
        // Given
        WorkerStreamContext<WorkerJobResponse> ctxA = createWorkerContext("worker-A", WORKER_GROUP_A, 10);
        WorkerStreamContext<WorkerJobResponse> ctxB = createWorkerContext("worker-B", WORKER_GROUP_B, 10);
        dispatcher.registerWorker(ctxA);
        dispatcher.registerWorker(ctxB);

        StreamObserver<WorkerJobResponse> obsA = ctxA.getResponseObserver();
        StreamObserver<WorkerJobResponse> obsB = ctxB.getResponseObserver();

        io.kestra.core.worker.MetadataChangePayload payload =
            new io.kestra.core.worker.MetadataChangePayload(
                io.kestra.core.worker.MetadataChangePayload.Type.NAMESPACE,
                "tenant-a", "prod.team");

        // When
        dispatcher.broadcastToAllWorkers(
            new io.kestra.core.worker.WorkerBroadcastEvent.MetadataChangeEvent(payload));

        // Then — each worker's underlying StreamObserver should have received an onNext
        verify(obsA).onNext(any(WorkerJobResponse.class));
        verify(obsB).onNext(any(WorkerJobResponse.class));
    }

}
