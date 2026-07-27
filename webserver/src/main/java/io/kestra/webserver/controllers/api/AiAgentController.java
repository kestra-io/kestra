package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentPrincipal;
import io.kestra.core.ai.agent.models.AgentThreadStatus;
import io.kestra.core.ai.agent.repositories.AiMessageRepositoryInterface;
import io.kestra.core.ai.agent.repositories.AiThreadRepositoryInterface;
import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.AgentConfiguration;
import io.kestra.webserver.services.ai.agent.AgentOrchestrator;
import io.kestra.webserver.services.ai.agent.AgentPrincipalResolver;
import io.kestra.webserver.services.ai.agent.AgentTurnContext;
import io.kestra.webserver.services.ai.agent.AiThreadManager;
import io.kestra.webserver.services.ai.agent.TurnEventSink;
import io.kestra.webserver.services.ai.agent.data.AgentEvents;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiConfirmActionRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiDecision;
import io.kestra.webserver.services.ai.agent.data.ApiThreadDetail;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.sse.Event;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

@Slf4j
@Controller("/api/v1/{tenant}/ai/threads")
@Requires(bean = AiServiceManager.class)
public class AiAgentController {
    protected final TenantService tenantService;
    private final AiServiceManager aiServiceManager;
    private final AiThreadRepositoryInterface threadStore;
    private final AiMessageRepositoryInterface messageStore;
    protected final AiThreadManager threadManager;
    private final AgentOrchestrator orchestrator;
    private final AgentPrincipalResolver principalResolver;
    private final int maxTurnsPerThread;
    private final int maxConcurrentTurns;
    private final Semaphore turnGate;
    private final ExecutorService executor;

    @Inject
    public AiAgentController(
        final TenantService tenantService,
        final AiServiceManager aiServiceManager,
        final AiThreadRepositoryInterface threadStore,
        final AiMessageRepositoryInterface messageStore,
        final AiThreadManager threadManager,
        final AgentOrchestrator orchestrator,
        final AgentPrincipalResolver principalResolver,
        final AgentConfiguration configuration,
        final ExecutorsUtils executorsUtils) {
        this.tenantService = tenantService;
        this.aiServiceManager = aiServiceManager;
        this.threadStore = threadStore;
        this.messageStore = messageStore;
        this.threadManager = threadManager;
        this.orchestrator = orchestrator;
        this.principalResolver = principalResolver;
        this.maxTurnsPerThread = configuration.maxTurnsPerThread();
        this.maxConcurrentTurns = configuration.maxConcurrentTurns();
        this.turnGate = new Semaphore(maxConcurrentTurns, false);
        this.executor = executorsUtils.cachedVirtualThreadPool("ai-agent-orchestrator");
    }

    @PreDestroy
    public void close() {
        ExecutorsUtils.closeExecutorService("ai-agent-orchestrator", executor, java.time.Duration.ofSeconds(10));
    }

    @Post
    @Operation(tags = { "AI" }, summary = "Create a Copilot conversation thread")
    public ApiThreadSummary create(@Body final ApiCreateThreadRequest request) {
        requireProvider(null);
        String tenant = tenantService.resolveTenant();
        Instant now = Instant.now();
        AgentThread thread = AgentThread.builder()
            .uid(IdUtils.create())
            .tenant(tenant)
            .userId(resolveUserId())
            .title(request.title())
            .mode(request.mode() != null ? request.mode() : AgentMode.ASK)
            .status(AgentThreadStatus.IDLE)
            .createdAt(now)
            .updatedAt(now)
            .deleted(false)
            .build();
        threadStore.create(thread);
        return ApiThreadSummary.from(thread);
    }

    @Get("/{threadId}")
    @Operation(tags = { "AI" }, summary = "Fetch a Copilot thread with its full message history")
    public ApiThreadDetail get(@PathVariable final String threadId) {
        requireProvider(null);
        String tenant = tenantService.resolveTenant();
        AgentThread thread = requireThread(tenant, threadId);
        return ApiThreadDetail.from(thread, messageStore.load(tenant, threadId));
    }

    @Post(uri = "/{threadId}/chat", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = { "AI" }, summary = "Open a streaming agent chat turn (SSE)")
    public Flux<Event<Object>> chat(
        @PathVariable final String threadId,
        @Body final ApiChatTurnRequest request) {
        String tenant = tenantService.resolveTenant();
        requireProvider(request.providerId());
        AgentThread thread = requireThread(tenant, threadId);

        // Cost/abuse guardrail: cap the number of user turns a single thread may hold. A resume reuses
        // its turn's trace, so confirming a parked action never counts against the cap.
        if (threadManager.turnCount(tenant, threadId) >= maxTurnsPerThread) {
            throw new HttpStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "This thread has reached its maximum of %d turns; start a new thread.".formatted(maxTurnsPerThread)
            );
        }

        AgentMode mode = request.mode() != null ? request.mode() : thread.mode();

        acquireTurnPermit();
        try {
            AgentThread running = threadManager.tryMarkRunning(thread, mode, AgentThreadStatus.IDLE)
                .orElseThrow(() -> new ConflictException("A turn is already in flight for thread '" + threadId + "'"));

            AgentPrincipal principal = principalResolver.resolve();
            return stream(
                sink -> orchestrator.runTurn(
                    new AgentTurnContext(running, request.prompt(), mode, tenant, request.providerId(), principal, request.additionalContext()), sink
                )
            );
        } catch (RuntimeException e) {
            turnGate.release();
            throw e;
        }
    }

    @Post(uri = "/{threadId}/confirm", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = { "AI" }, summary = "Confirm a proposed action and stream the resumed turn (SSE)")
    public Flux<Event<Object>> confirm(
        @PathVariable final String threadId,
        @Body final ApiConfirmActionRequest request) {
        String tenant = tenantService.resolveTenant();
        requireProvider(request.providerId());
        AgentThread thread = requireThread(tenant, threadId);

        // The confirmation token is persisted on the thread, so the pending action is matched from the
        // durable state rather than in-memory — a resume can land on any node, not only the one that
        // suspended the turn.
        if (
            thread.status() != AgentThreadStatus.AWAITING_CONFIRMATION
                || !request.confirmationId().equals(thread.pendingConfirmationId())
        ) {
            throw new NotFoundException("No pending action for confirmationId %s".formatted(request.confirmationId()));
        }

        acquireTurnPermit();
        try {
            AgentThread running = threadManager.tryMarkRunning(thread, thread.mode(), AgentThreadStatus.AWAITING_CONFIRMATION)
                .orElseThrow(() -> new ConflictException("A turn is already in flight for thread %s".formatted(threadId)));

            boolean approve = request.decision() == ApiDecision.APPROVE;

            AgentPrincipal principal = principalResolver.resolve();
            return stream(sink -> orchestrator.resume(running, request.providerId(), approve, request.reason(), principal, sink));
        } catch (RuntimeException e) {
            turnGate.release();
            throw e;
        }
    }

    /**
     * Acquires a turn permit or rejects with 429 when the node is at capacity. Saturation is transient and
     * retryable, so {@code TOO_MANY_REQUESTS} is returned rather than queueing the turn silently.
     */
    private void acquireTurnPermit() {
        if (!turnGate.tryAcquire()) {
            throw new HttpStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "AI Copilot is at capacity (%d concurrent turns); retry shortly.".formatted(maxConcurrentTurns)
            );
        }
    }

    /**
     * Offloads the turn onto a virtual thread and adapts it onto an SSE {@link Flux}. Takes ownership of the
     * turn permit acquired by the caller: the permit is released exactly once — either when the offloaded
     * task finishes (success or error) or when the client cancels before the task starts — guarded by an
     * {@link AtomicBoolean} so the two paths never double-release.
     */
    private Flux<Event<Object>> stream(final Consumer<TurnEventSink> work) {
        return Flux.create(emitter ->
        {
            FluxTurnEventSink sink = new FluxTurnEventSink(emitter);
            AtomicBoolean released = new AtomicBoolean();
            Runnable release = () -> {
                if (released.compareAndSet(false, true)) {
                    turnGate.release();
                }
            };
            emitter.onCancel(() ->
            {
                sink.markCancelled();
                release.run();
            });
            executor.execute(() ->
            {
                try {
                    work.accept(sink);
                } catch (Exception e) {
                    sink.error(e);
                } finally {
                    release.run();
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    protected AgentThread requireThread(final String tenant, final String threadId) {
        String userId = resolveUserId();
        return threadStore.find(tenant, threadId)
            .filter(thread -> userId == null || Objects.equals(thread.userId(), userId))
            .orElseThrow(() -> new NotFoundException("Thread not found: '" + threadId + "'"));
    }

    protected String resolveUserId() {
        return null;
    }

    private void requireProvider(final String providerId) {
        // The agentic loop needs a configured, streaming-capable provider. Reject up front with 503 when
        // none is configured or the requested provider is unknown, rather than letting a turn fail mid-stream.
        if (!aiServiceManager.hasConfiguredProvider() || aiServiceManager.getAiService(providerId) == null) {
            throw new HttpStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI Copilot is not available: no AI provider is configured or reachable.");
        }
    }

    /** Adapts the orchestrator's {@link TurnEventSink} onto a Reactor {@link FluxSink} of SSE events. */
    private static final class FluxTurnEventSink implements TurnEventSink {
        private final FluxSink<Event<Object>> emitter;
        private volatile boolean cancelled;

        private FluxTurnEventSink(final FluxSink<Event<Object>> emitter) {
            this.emitter = emitter;
        }

        void markCancelled() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public void emit(final String event, final Object payload) {
            emitter.next(Event.of(payload).name(event));
        }

        @Override
        public void complete() {
            emitter.complete();
        }

        @Override
        public void error(final Throwable error) {
            String message = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
            emit(AgentEvents.ERROR, new AgentEvents.ErrorEvent(message));
            complete();
        }
    }
}
