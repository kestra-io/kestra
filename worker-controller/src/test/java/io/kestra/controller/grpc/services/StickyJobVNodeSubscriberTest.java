package io.kestra.controller.grpc.services;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.executor.command.RestartWorkingDirectory;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.KeyedDispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.runners.WorkerJob;
import io.kestra.core.runners.WorkerJobEvent;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.server.ServiceType;
import io.kestra.core.utils.Disposable;
import io.kestra.core.utils.Either;
import io.kestra.core.vnodes.ControllerVNodesAssignmentChannel;
import io.kestra.core.vnodes.VNodesAssignmentEvent;
import io.kestra.core.worker.StickyWorkerRouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StickyJobVNodeSubscriberTest {

    private static final WorkerJobEvent BUSY_WORKER_JOB = new WorkerJobEvent(
        null,
        mock(WorkerJob.class),
        new WorkerJobEvent.StickyRouting("worker-1", StickyWorkerRouting.routingKey(3))
    );

    private final WorkerJobDispatcher dispatcher = mock(WorkerJobDispatcher.class);
    private final StickyWorkerRouting routing = mock(StickyWorkerRouting.class);
    private final KeyedDispatchQueueInterface<WorkerJobEvent> queue = mock(KeyedDispatchQueueInterface.class);
    private final DispatchQueueInterface<ExecutionCommand> executionCommandQueue = mock(DispatchQueueInterface.class);
    private final QueueSubscriber<WorkerJobEvent> vNodeSubscriber = mock(QueueSubscriber.class);
    private final StickyJobVNodeSubscriber subscriber = subscriber();

    private Consumer<Either<WorkerJobEvent, DeserializationException>> vNodeConsumer;

    @BeforeEach
    void assignVNode() {
        ArgumentCaptor<Consumer<Either<WorkerJobEvent, DeserializationException>>> consumer = ArgumentCaptor.captor();
        verify(vNodeSubscriber).subscribe(consumer.capture());
        vNodeConsumer = consumer.getValue();
        when(dispatcher.dispatchSticky(BUSY_WORKER_JOB)).thenReturn(WorkerJobDispatcher.StickyDispatchResult.WORKER_BUSY);
    }

    @Test
    void shouldPauseTheVNodeOfABusyWorkerUntilItFreesAPermit() {
        when(dispatcher.isWorkerBusy("worker-1", null)).thenReturn(true);

        vNodeConsumer.accept(Either.left(BUSY_WORKER_JOB));
        subscriber.onWorkerCapacityChanged(worker("worker-2"));

        verify(vNodeSubscriber).pause();
        verify(vNodeSubscriber, never()).resume();

        when(dispatcher.isWorkerBusy("worker-1", null)).thenReturn(false);
        subscriber.onWorkerCapacityChanged(worker("worker-1"));

        verify(vNodeSubscriber).resume();
    }

    @Test
    void shouldResumeTheVNodeAtOnceWhenTheWorkerFreedAPermitBeforeThePause() {
        when(dispatcher.isWorkerBusy("worker-1", null)).thenReturn(false);

        vNodeConsumer.accept(Either.left(BUSY_WORKER_JOB));

        InOrder inOrder = inOrder(vNodeSubscriber);
        inOrder.verify(vNodeSubscriber).pause();
        inOrder.verify(vNodeSubscriber).resume();
    }

    @Test
    void shouldKeepForwardingTheJobOfARunningWorkerNoControllerHolds() throws QueueException {
        WorkerJobEvent job = notConnectedJob(null);
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Unknown());

        vNodeConsumer.accept(Either.left(job));

        ArgumentCaptor<WorkerJobEvent> forwarded = ArgumentCaptor.captor();
        verify(queue).emit(eq(StickyWorkerRouting.routingKey(3)), forwarded.capture());
        assertThat(forwarded.getValue().stickyRouting().workerUnknownSince()).isNotNull();
    }

    @Test
    void shouldRestartTheWorkingDirectoryOfAJobWhoseWorkerIsLost() throws QueueException {
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Lost());
        TaskRun taskRun = TaskRun.builder().id("child-run").executionId("execution").taskId("child").state(new State()).build();
        WorkerTask workerTask = WorkerTask.builder().taskRun(taskRun).workingDirectoryId("working-directory-run").build();

        WorkerJobEvent job = new WorkerJobEvent(null, workerTask, new WorkerJobEvent.StickyRouting("worker-2", StickyWorkerRouting.routingKey(3)));
        when(dispatcher.dispatchSticky(job)).thenReturn(WorkerJobDispatcher.StickyDispatchResult.WORKER_NOT_CONNECTED);

        vNodeConsumer.accept(Either.left(job));

        ArgumentCaptor<ExecutionCommand> command = ArgumentCaptor.captor();
        verify(executionCommandQueue).emit(command.capture());
        assertThat(command.getValue()).isInstanceOfSatisfying(RestartWorkingDirectory.class, restart ->
        {
            assertThat(restart.workingDirectoryTaskRunId()).isEqualTo("working-directory-run");
            assertThat(restart.lostTaskRunId()).isEqualTo("child-run");
        });
        verify(queue, never()).emit(any(), any(WorkerJobEvent.class));
        verify(dispatcher, never()).failSticky(any());
    }

    @Test
    void shouldFailAJobWhoseRunningWorkerNoControllerHoldsForLongerThanTheLivenessCoordinatorTakesToDeclareAWorkerLost() throws QueueException {
        WorkerJobEvent job = notConnectedJob(Instant.now().minus(Duration.ofMinutes(10)));
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Unknown());

        vNodeConsumer.accept(Either.left(job));

        verify(dispatcher).failSticky(job);
        verify(queue, never()).emit(any(), any(WorkerJobEvent.class));
    }

    @Test
    void shouldForwardTheJobOfAFoundWorkerHoweverLongItWasUnknown() throws QueueException {
        WorkerJobEvent job = notConnectedJob(Instant.now().minus(Duration.ofMinutes(10)));
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Held(StickyWorkerRouting.routingKey(7)));

        vNodeConsumer.accept(Either.left(job));

        ArgumentCaptor<WorkerJobEvent> forwarded = ArgumentCaptor.captor();
        verify(queue).emit(eq(StickyWorkerRouting.routingKey(7)), forwarded.capture());
        assertThat(forwarded.getValue().stickyRouting().workerUnknownSince()).isNull();
        verify(dispatcher, never()).failSticky(any());
    }

    @Test
    void shouldCountTheWorkerAsUnknownWhenItsLivenessStillPointsAtThisVNode() throws QueueException {
        WorkerJobEvent job = notConnectedJob(null);
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Held(StickyWorkerRouting.routingKey(3)));

        vNodeConsumer.accept(Either.left(job));

        ArgumentCaptor<WorkerJobEvent> forwarded = ArgumentCaptor.captor();
        verify(queue).emit(eq(StickyWorkerRouting.routingKey(3)), forwarded.capture());
        assertThat(forwarded.getValue().stickyRouting().workerUnknownSince()).isNotNull();
    }

    @Test
    void shouldFailTheJobWhenItCannotBeForwarded() throws QueueException {
        WorkerJobEvent job = notConnectedJob(null);
        when(routing.locate("worker-2")).thenReturn(new StickyWorkerRouting.WorkerLocation.Held(StickyWorkerRouting.routingKey(7)));
        doThrow(new QueueException("unavailable")).when(queue).emit(eq(StickyWorkerRouting.routingKey(7)), any(WorkerJobEvent.class));

        vNodeConsumer.accept(Either.left(job));

        verify(dispatcher).failSticky(job);
    }

    @Test
    void shouldResumeTheVNodeOfABusyWorkerThatLeftTheWorkerQueue() {
        WorkerJobEvent job = new WorkerJobEvent("gpu", mock(WorkerJob.class), new WorkerJobEvent.StickyRouting("worker-1", StickyWorkerRouting.routingKey(3)));
        when(dispatcher.dispatchSticky(job)).thenReturn(WorkerJobDispatcher.StickyDispatchResult.WORKER_BUSY);
        when(dispatcher.isWorkerBusy("worker-1", "gpu")).thenReturn(true);

        vNodeConsumer.accept(Either.left(job));
        subscriber.onWorkerSubscriptionsChanged(worker("worker-1"), Set.of(), Set.of("gpu"));

        verify(vNodeSubscriber).resume();
    }

    private WorkerJobEvent notConnectedJob(Instant workerUnknownSince) {
        WorkerJobEvent job = new WorkerJobEvent(
            null,
            mock(WorkerJob.class),
            new WorkerJobEvent.StickyRouting("worker-2", StickyWorkerRouting.routingKey(3), workerUnknownSince)
        );
        when(dispatcher.dispatchSticky(job)).thenReturn(WorkerJobDispatcher.StickyDispatchResult.WORKER_NOT_CONNECTED);
        return job;
    }

    private StickyJobVNodeSubscriber subscriber() {
        when(queue.subscriber(StickyWorkerRouting.routingKey(3))).thenReturn(vNodeSubscriber);
        ControllerVNodesAssignmentChannel channel = mock(ControllerVNodesAssignmentChannel.class);
        when(channel.serviceType()).thenReturn(ServiceType.CONTROLLER);
        ArgumentCaptor<Consumer<VNodesAssignmentEvent>> assignments = ArgumentCaptor.captor();
        when(channel.subscribe(assignments.capture())).thenReturn(Disposable.of(() -> {}));

        ServerConfig serverConfig = mock(ServerConfig.class);
        when(serverConfig.liveness()).thenReturn(new ServerConfig.Liveness(true, Duration.ofSeconds(5), Duration.ofSeconds(45), Duration.ofSeconds(45), Duration.ofSeconds(3)));
        StickyJobVNodeSubscriber stickyJobVNodeSubscriber = new StickyJobVNodeSubscriber(queue, executionCommandQueue, routing, channel, serverConfig);
        stickyJobVNodeSubscriber.init(dispatcher);
        stickyJobVNodeSubscriber.start("controller-1");

        Instant epoch = Instant.now();
        assignments.getValue().accept(new VNodesAssignmentEvent.VNodesAssignmentRequest(epoch, ServiceType.CONTROLLER, "leader", epoch, Set.of("controller-1")));
        assignments.getValue().accept(new VNodesAssignmentEvent.VNodesAssignmentRelease(epoch, ServiceType.CONTROLLER, "leader", epoch, Map.of("controller-1", Set.of(3))));
        return stickyJobVNodeSubscriber;
    }

    private static WorkerStreamContext<?> worker(String workerId) {
        WorkerStreamContext<?> context = mock(WorkerStreamContext.class);
        when(context.getWorkerId()).thenReturn(workerId);
        return context;
    }
}
