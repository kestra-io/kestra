package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.ExecutorsUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.AgentOrchestrator;
import io.kestra.webserver.services.ai.agent.ConfirmationRegistry;
import io.kestra.webserver.services.ai.agent.SuspendedTurn;
import io.kestra.webserver.services.ai.agent.TurnEventSink;
import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;
import io.kestra.webserver.services.ai.agent.data.ApiChatTurnRequest;
import io.kestra.webserver.services.ai.agent.data.ApiConfirmActionRequest;
import io.kestra.webserver.services.ai.agent.data.ApiCreateThreadRequest;
import io.kestra.webserver.services.ai.agent.data.ApiDecision;
import io.kestra.webserver.services.ai.agent.data.ApiThreadDetail;
import io.kestra.webserver.services.ai.agent.data.ApiThreadSummary;
import io.kestra.webserver.services.ai.agent.store.MessageStore;
import io.kestra.webserver.services.ai.agent.store.ThreadStore;

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
    private final TenantService tenantService;
    private final AiServiceManager aiServiceManager;
    private final ThreadStore threadStore;
    private final MessageStore messageStore;
    private final AgentOrchestrator orchestrator;
    private final ConfirmationRegistry confirmationRegistry;
    private final ExecutorService executor;

    @Inject
    public AiAgentController(
        final TenantService tenantService,
        final AiServiceManager aiServiceManager,
        final ThreadStore threadStore,
        final MessageStore messageStore,
        final AgentOrchestrator orchestrator,
        final ConfirmationRegistry confirmationRegistry,
        final ExecutorsUtils executorsUtils) {
        this.tenantService = tenantService;
        this.aiServiceManager = aiServiceManager;
        this.threadStore = threadStore;
        this.messageStore = messageStore;
        this.orchestrator = orchestrator;
        this.confirmationRegistry = confirmationRegistry;
        this.executor = executorsUtils.maxCachedThreadPool(8, "ai-agent-orchestrator");
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
            .title(request.title())
            .mode(request.mode() != null ? request.mode() : AgentMode.ASK)
            .scope(request.scope())
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
        return ApiThreadDetail.from(thread, messageStore.load(threadId));
    }

    @Post(uri = "/{threadId}/chat", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = { "AI" }, summary = "Open a streaming agent chat turn (SSE)")
    public Flux<Event<Object>> chat(
        @PathVariable final String threadId,
        @Body final ApiChatTurnRequest request) {
        String tenant = tenantService.resolveTenant();
        requireProvider(request.providerId());
        AgentThread thread = requireThread(tenant, threadId);

        if (thread.status() != AgentThreadStatus.IDLE) {
            throw new HttpStatusException(HttpStatus.CONFLICT, "A turn is already in flight for thread '" + threadId + "'");
        }

        AgentMode mode = request.mode() != null ? request.mode() : thread.mode();
        return stream(sink -> orchestrator.runTurn(thread, request.prompt(), mode, tenant, request.providerId(), sink));
    }

    @Post(uri = "/{threadId}/confirm", produces = MediaType.TEXT_EVENT_STREAM)
    @Operation(tags = { "AI" }, summary = "Confirm a proposed action and stream the resumed turn (SSE)")
    public Flux<Event<Object>> confirm(
        @PathVariable final String threadId,
        @Body final ApiConfirmActionRequest request) {
        String tenant = tenantService.resolveTenant();
        requireProvider(null);
        requireThreadExists(tenant, threadId);

        SuspendedTurn turn = confirmationRegistry.take(request.confirmationId())
            .filter(t -> t.threadId().equals(threadId) && t.tenant().equals(tenant))
            .orElseThrow(() -> new NotFoundException("No pending action for confirmationId '" + request.confirmationId() + "'"));

        boolean approve = request.decision() == ApiDecision.APPROVE;
        return stream(sink -> orchestrator.resume(turn, approve, request.reason(), sink));
    }

    private Flux<Event<Object>> stream(final Consumer<TurnEventSink> work) {
        return Flux.create(emitter ->
        {
            FluxTurnEventSink sink = new FluxTurnEventSink(emitter);
            emitter.onCancel(sink::markCancelled);
            executor.execute(() ->
            {
                try {
                    work.accept(sink);
                } catch (Exception e) {
                    sink.error(e);
                }
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    private AgentThread requireThread(final String tenant, final String threadId) {
        return threadStore.find(tenant, threadId)
            .orElseThrow(() -> new NotFoundException("Thread not found: '" + threadId + "'"));
    }

    private void requireThreadExists(final String tenant, final String threadId) {
        if (!threadStore.exists(tenant, threadId)) {
            throw new NotFoundException("Thread not found: '" + threadId + "'");
        }
    }

    private void requireProvider(final String providerId) {
        // The agentic loop needs a real, streaming-capable provider. Without one, the manager
        // falls back to a non-streaming ApiAiService, so a null-check alone lets the turn start
        // and fail mid-stream — check hasConfiguredProvider() so we reject up front with 503.
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
            emitter.error(error);
        }
    }
}
