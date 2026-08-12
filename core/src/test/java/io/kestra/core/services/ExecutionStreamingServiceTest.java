package io.kestra.core.services;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.ExecutionEventType;
import io.kestra.core.runners.FollowExecutionEvent;
import io.kestra.core.utils.Either;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ExecutionStreamingService}.
 */
@KestraTest
class ExecutionStreamingServiceTest {

    @Inject
    private BroadcastQueueInterface<FollowExecutionEvent> executionEventQueue;

    @Inject
    private ExecutionStreamingService executionStreamingService;

    @Inject
    private ExecutionRepositoryInterface executionRepositoryInterface;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    /**
     * Core contract for the fix of kestra-io/kestra-ee#8824: when a {@code TERMINATED} {@link FollowExecutionEvent}
     * arrives and the execution is already findable in the repository, the streaming
     * {@link Flux} must complete.
     */
    @Test
    void shouldCompleteFluxWhenTerminatedEventArrivesAndExecutionIsPersistedInRepository() throws Exception {
        // Given — a flow and a FAILED execution (mimicking what the executor exception path
        // now persists before emitting the follow event)
        var flow = flowRepositoryInterface.create(
            GenericFlow.of(
                Flow.builder()
                    .tenantId(null)
                    .namespace("io.kestra.tests")
                    .id(IdUtils.create())
                    .tasks(
                        Collections.singletonList(
                            Log.builder().id("log").type(Log.class.getName()).message("test").build()
                        )
                    )
                    .build()
            )
        );

        var execution = Execution.newExecution(flow, Collections.emptyList())
            .withState(State.Type.FAILED);

        // Register the subscriber BEFORE saving the execution so the post-registration
        // guard in registerSubscriber does not fire prematurely.
        var subscriberId = IdUtils.create();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        Flux.<Event<Execution>> create(
            sink -> executionStreamingService.registerSubscriber(execution.getId(), subscriberId, sink, flow)
        )
            .timeout(java.time.Duration.ofSeconds(5))
            .doFinally(sig -> executionStreamingService.unregisterSubscriber(execution.getId(), subscriberId))
            .subscribe(
                event ->
                {
                    /* progress events — not relevant here */ },
                completed::completeExceptionally,
                () -> completed.complete(null) // sink.complete() unblocks the webhook
            );

        // Persist the execution into the repository — this is what the fixed executor
        // failure path now does (returning a non-null ExecutorContext from the lock lambda
        // instead of null). Without this, findById() returns empty and the event is dropped.
        executionRepositoryInterface.save(execution);

        // Emit the TERMINATED FollowExecutionEvent — this is what the fixed executor now
        // emits (TERMINATED, not UPDATED) post-commit, outside the lock lambda.
        executionEventQueue.emit(new FollowExecutionEvent(execution, ExecutionEventType.TERMINATED));

        // Then — the Flux must complete within a reasonable time, meaning ExecutionStreamingService
        // called sink.complete() after finding the terminal execution in the repository.
        // A timeout here means the event was dropped (e.g. findById returned empty) or the
        // event type was not TERMINATED, reproducing the webhook wait:true hang.
        assertThat(completed.get(5, TimeUnit.SECONDS)).isNull();
    }

    /**
     * Guards against kestra-io/kestra-ee#8824 reappearing: the internal fan-out lookup runs on a
     * queue-polling thread with no authenticated principal, so EE's ACL-enforcing {@code findById}
     * always denies there. This pins that the streaming service reads via {@code findByIdWithoutAcl}
     * instead — mocking {@code findById} to always deny reproduces the EE hang if the service
     * regresses to using it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void shouldCompleteFluxWhenTheAclFilteredLookupWouldDenyTheExecution() throws Exception {
        // Given — a flow and an execution, exposed only through findByIdWithoutAcl (findById always
        // denies, simulating EE's ACL falseCondition() fallback on a non-request thread)
        var flow = Flow.builder()
            .tenantId(null)
            .namespace("io.kestra.tests")
            .id(IdUtils.create())
            .tasks(Collections.singletonList(Log.builder().id("log").type(Log.class.getName()).message("test").build()))
            .build();

        var runningExecution = Execution.newExecution(flow, Collections.emptyList()).withState(State.Type.RUNNING);
        var terminalExecution = runningExecution.withState(State.Type.SUCCESS);

        var executionRepository = mock(ExecutionRepositoryInterface.class);
        when(executionRepository.findById(any(), any())).thenReturn(Optional.empty());
        when(executionRepository.findByIdWithoutAcl(flow.getTenantId(), runningExecution.getId()))
            .thenReturn(Optional.of(runningExecution)) // registerSubscriber's catch-up guard: not yet terminal
            .thenReturn(Optional.of(terminalExecution)); // queue-consumer fan-out: now terminal

        var executionService = mock(ExecutionService.class);
        when(executionService.isTerminated(any(), any())).thenAnswer(invocation ->
            ((Execution) invocation.getArgument(1)).getState().isTerminated());

        AtomicReference<Consumer<Either<FollowExecutionEvent, DeserializationException>>> queueConsumer = new AtomicReference<>();
        var queueSubscriber = mock(QueueSubscriber.class);
        doAnswer(invocation ->
        {
            queueConsumer.set(invocation.getArgument(0));
            return queueSubscriber;
        }).when(queueSubscriber).subscribe(any());

        var executionQueue = mock(BroadcastQueueInterface.class);
        when(executionQueue.subscriber()).thenReturn(queueSubscriber);

        var executionStreamingService = new ExecutionStreamingService(executionQueue, executionService, executionRepository);
        executionStreamingService.startQueueConsumer();

        var subscriberId = IdUtils.create();
        CompletableFuture<Void> completed = new CompletableFuture<>();

        Flux.<Event<Execution>> create(
            sink -> executionStreamingService.registerSubscriber(runningExecution.getId(), subscriberId, sink, flow)
        )
            .timeout(java.time.Duration.ofSeconds(5))
            .doFinally(sig -> executionStreamingService.unregisterSubscriber(runningExecution.getId(), subscriberId))
            .subscribe(
                event -> { /* progress events — not relevant here */ },
                completed::completeExceptionally,
                () -> completed.complete(null)
            );

        // Deliver the TERMINATED FollowExecutionEvent through the captured queue consumer, exactly
        // as the real queue-polling thread would — with no request context in scope.
        queueConsumer.get().accept(Either.left(new FollowExecutionEvent(terminalExecution, ExecutionEventType.TERMINATED)));

        assertThat(completed.get(5, TimeUnit.SECONDS)).isNull();
    }
}
