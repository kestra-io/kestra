package io.kestra.plugin.core.trigger;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Striped;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerEvaluationResult;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TruthUtils;
import io.kestra.core.validations.BatchWebhookValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import reactor.core.publisher.Mono;

@SuperBuilder
@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow after buffering webhook events in batches.",
    description = """
        Exposes the same authenticated webhook URL shape as the Webhook trigger (`.../executions/webhook/{namespace}/{flowId}/{key}`), but does not start one execution per request. Events are buffered and an execution is created when:
        - `eventsCount` events have been received, or
        - `pollingInterval` has elapsed and at least one event is buffered.

        At least one of `eventsCount` or `pollingInterval` must be set. When both are set, whichever condition is met first flushes the buffer.

        Trigger output is an ION file (`trigger.uri`) with one row per event. Each row contains `eventId`, `eventReceived`, `body`, `headers`, and `parameters`.

        Use `eventFilter` (not `when`) to accept or discard individual webhook events based on their payload. `eventFilter` is evaluated per request with that event as `trigger` (so `trigger.body`, `trigger.headers`, and `trigger.parameters` are available). Discarded events return HTTP 204 and are not buffered.

        The standard trigger `when` expression is evaluated by the scheduler at flush time and only has `trigger.date` available — it must not reference the event payload. Use `when` only for schedule-level gates (for example time-of-day).

        ::alert{type="warning"}
        Event buffering uses a read-modify-write on the namespace KV store. Concurrency is coordinated with an in-process lock only. In the default split webserver/worker deployment (and with multiple webserver replicas), concurrent accepts and flushes for the same flow/trigger can race and drop or duplicate events. Prefer a single-process / low-concurrency setup for high-throughput batch webhooks, or treat buffering as best-effort until a durable queue is available.
        ::"""
)
@Plugin(
    examples = {
        @Example(
            title = "Start one execution for every 100 webhook events.",
            full = true,
            code = """
                id: batch_webhook_count
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Processing {{ trigger.count }} events from {{ trigger.uri }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.BatchWebhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    eventsCount: 100
                """
        ),
        @Example(
            title = "Start one execution every 15 minutes if at least one event was received.",
            full = true,
            code = """
                id: batch_webhook_interval
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Processing {{ trigger.count }} events from {{ trigger.uri }}"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.BatchWebhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    pollingInterval: PT15M
                """
        ),
        @Example(
            title = "Batch with a per-event filter using `eventFilter` (not `when`).",
            full = true,
            code = """
                id: batch_webhook_filter
                namespace: company.team

                tasks:
                  - id: log
                    type: io.kestra.plugin.core.log.Log
                    message: "Processing {{ trigger.count }} events"

                triggers:
                  - id: webhook
                    type: io.kestra.plugin.core.trigger.BatchWebhook
                    key: 4wjtkzwVGBM9yKnjm3yv8r
                    eventsCount: 10
                    eventFilter: "{{ trigger.body.hello == 'world' }}"
                """
        )
    }
)
@BatchWebhookValidation
public class BatchWebhook extends AbstractWebhookTrigger implements PollingTriggerInterface, TriggerOutput<BatchWebhook.Output> {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson().copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.USE_DEFAULTS);

    private static final TypeReference<BufferState> BUFFER_TYPE = new TypeReference<>() {
    };

    private static final Striped<Lock> BUFFER_LOCKS = Striped.lock(64);

    /**
     * Scheduler poll interval when {@code eventsCount} is set so count thresholds are observed promptly.
     * Kept short intentionally; empty buffers return immediately from {@link #eval}.
     */
    private static final Duration COUNT_POLL_INTERVAL = Duration.ofSeconds(1);

    @Min(1)
    @Schema(
        title = "Number of events required to trigger an execution.",
        description = "When the buffer reaches this size, an execution is started and that many events are drained from the buffer. Optional if `pollingInterval` is set."
    )
    @PluginProperty
    private Integer eventsCount;

    @Schema(
        title = "Maximum time to wait before flushing a non-empty buffer.",
        description = """
            When set, the scheduler evaluates this trigger on this interval. If at least one event is buffered and the time since the first buffered event is greater than or equal to this duration, an execution is started with all buffered events.
            Must be strictly positive (zero is rejected). Optional if `eventsCount` is set."""
    )
    @PluginProperty
    private Duration pollingInterval;

    @Schema(
        title = "Per-event filter expression evaluated when a webhook request is received.",
        description = """
            Pebble expression evaluated against the incoming event (`trigger.body`, `trigger.headers`, `trigger.parameters`).
            When the expression is falsy, the event is discarded with HTTP 204 and is not buffered.
            Defaults to accepting every event.

            Do not put payload filters in `when` — the scheduler re-evaluates `when` at flush time without the event payload, which would prevent batches from ever flushing."""
    )
    @PluginProperty
    private String eventFilter;

    /**
     * Polling interval for the scheduler.
     * <p>
     * When {@code eventsCount} is set, polls every second so count thresholds are observed promptly.
     * Otherwise uses {@code pollingInterval}.
     */
    @Override
    public Duration getInterval() {
        if (this.eventsCount != null) {
            return COUNT_POLL_INTERVAL;
        }
        return this.pollingInterval;
    }

    @Override
    public Mono<HttpResponse<?>> evaluate(WebhookContext context) throws Exception {
        if (context.path() != null || context.request().getUri().getPath().endsWith("/")) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.NOT_FOUND));
        }

        String body = context.request().getBody() != null ? (String) context.request().getBody().getContent() : null;

        Object parsedBody = tryMap(body)
            .or(() -> tryArray(body))
            .orElse(body);

        Map<String, List<String>> headers = context.request().getHeaders() != null
            ? context.request().getHeaders().map()
            : null;
        Map<String, List<String>> parameters = context.webhookService().parseParameters(context);

        BufferedEvent event = new BufferedEvent(
            IdUtils.create(),
            Instant.now(),
            parsedBody,
            headers,
            parameters
        );

        // Per-event filter only — do not use trigger `when` here. The scheduler re-evaluates `when`
        // at flush time without event payload variables, so payload filters must use `eventFilter`.
        EventProbeOutput probe = EventProbeOutput.builder()
            .body(parsedBody)
            .headers(headers)
            .parameters(parameters)
            .build();

        if (!matchesEventFilter(context, probe)) {
            return Mono.just(HttpResponse.of(HttpResponse.Status.NO_CONTENT));
        }

        RunContext runContext = context.webhookService().runContext(context.flow(), this);
        int buffered = appendEvent(runContext, context.flow(), event);

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("status", "buffered");
        responseBody.put("eventsBuffered", buffered);

        return Mono.just(HttpResponse.of(HttpResponse.Status.ACCEPTED, responseBody));
    }

    @Override
    public Optional<TriggerEvaluationResult> eval(ConditionContext conditionContext, TriggerContext context) throws Exception {
        RunContext runContext = conditionContext.getRunContext();

        Optional<List<BufferedEvent>> maybeEvents = takeEventsForFlush(runContext, conditionContext.getFlow());
        if (maybeEvents.isEmpty() || maybeEvents.get().isEmpty()) {
            return Optional.empty();
        }

        List<BufferedEvent> events = maybeEvents.get();
        Output output = writeIonFile(runContext, events);
        return Optional.of(TriggerService.generateEvaluationResult(this, conditionContext, output));
    }

    /**
     * Evaluates {@link #eventFilter} against a single incoming event.
     * <p>
     * Returns {@code true} when the filter is unset/blank or evaluates to a truthy value.
     * Evaluation failures discard the event (same as a falsy filter) so malformed filters do not
     * poison the buffer.
     */
    private boolean matchesEventFilter(WebhookContext context, EventProbeOutput probe) {
        if (this.eventFilter == null || this.eventFilter.isBlank()) {
            return true;
        }

        Execution probeExecution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(context.flow().getTenantId())
            .namespace(context.flow().getNamespace())
            .flowId(context.flow().getId())
            .flowRevision(context.flow().getRevision())
            .state(new State())
            .trigger(ExecutionTrigger.of(this, probe))
            .build();

        RunContext runContext = context.webhookService().runContext(context.flow(), probeExecution);
        try {
            return TruthUtils.isTruthy(runContext.render(this.eventFilter));
        } catch (IllegalVariableEvaluationException e) {
            runContext.logger().warn(
                "Unable to evaluate BatchWebhook eventFilter for flow '{}/{}', event discarded: {}",
                context.flow().getNamespace(),
                context.flow().getId(),
                e.getMessage()
            );
            return false;
        }
    }

    private int appendEvent(RunContext runContext, FlowInterface flow, BufferedEvent event)
        throws IOException, ResourceExpiredException {
        String key = bufferKey(flow);
        Lock lock = BUFFER_LOCKS.get(key);
        lock.lock();
        try {
            BufferState state = readBuffer(runContext, key);
            List<BufferedEvent> events = new ArrayList<>(state.events());
            Instant firstEventAt = state.firstEventAt() != null ? state.firstEventAt() : event.eventReceived();
            events.add(event);
            writeBuffer(runContext, key, new BufferState(events, firstEventAt));
            return events.size();
        } finally {
            lock.unlock();
        }
    }

    private Optional<List<BufferedEvent>> takeEventsForFlush(RunContext runContext, FlowInterface flow)
        throws IOException, ResourceExpiredException {
        String key = bufferKey(flow);
        Lock lock = BUFFER_LOCKS.get(key);
        lock.lock();
        try {
            BufferState state = readBuffer(runContext, key);
            List<BufferedEvent> events = state.events();
            if (events.isEmpty()) {
                return Optional.empty();
            }

            boolean countReached = this.eventsCount != null && events.size() >= this.eventsCount;
            boolean shouldFlush;
            if (this.eventsCount != null && this.pollingInterval != null) {
                boolean intervalReached = state.firstEventAt() != null
                    && !Instant.now().isBefore(state.firstEventAt().plus(this.pollingInterval));
                shouldFlush = countReached || intervalReached;
            } else if (this.eventsCount != null) {
                shouldFlush = countReached;
            } else {
                // Interval-only mode: the scheduler already polls every pollingInterval.
                shouldFlush = true;
            }

            if (!shouldFlush) {
                return Optional.empty();
            }

            List<BufferedEvent> toFlush;
            List<BufferedEvent> remaining;
            if (countReached) {
                int n = this.eventsCount;
                toFlush = new ArrayList<>(events.subList(0, n));
                remaining = new ArrayList<>(events.subList(n, events.size()));
            } else {
                toFlush = new ArrayList<>(events);
                remaining = List.of();
            }

            if (remaining.isEmpty()) {
                deleteBuffer(runContext, key);
            } else {
                Instant nextFirst = remaining.getFirst().eventReceived();
                writeBuffer(runContext, key, new BufferState(remaining, nextFirst));
            }

            return Optional.of(toFlush);
        } finally {
            lock.unlock();
        }
    }

    private Output writeIonFile(RunContext runContext, List<BufferedEvent> events) throws IOException {
        Path tempFile = runContext.workingDir().createTempFile(".ion");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile.toFile()), FileSerde.BUFFER_SIZE)) {
            for (BufferedEvent event : events) {
                FileSerde.write(output, event.toRow());
            }
        }
        URI uri = runContext.storage().putFile(tempFile.toFile());
        return Output.builder()
            .uri(uri)
            .count(events.size())
            .build();
    }

    private String bufferKey(FlowInterface flow) {
        return "batchwebhook." + IdUtils.fromParts(
            flow.getTenantId() != null ? flow.getTenantId() : "main",
            flow.getNamespace().replace('.', '-'),
            flow.getId(),
            this.getId()
        );
    }

    private BufferState readBuffer(RunContext runContext, String key) throws IOException, ResourceExpiredException {
        KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        Optional<io.kestra.core.storages.kv.KVValue> value = kv.getValue(key);
        if (value.isEmpty() || value.get().value() == null) {
            return BufferState.empty();
        }

        Object raw = value.get().value();
        if (raw instanceof byte[] b) {
            return MAPPER.readValue(b, BUFFER_TYPE);
        } else if (raw instanceof String s) {
            return MAPPER.readValue(s.getBytes(StandardCharsets.UTF_8), BUFFER_TYPE);
        }
        return MAPPER.convertValue(raw, BUFFER_TYPE);
    }

    private void writeBuffer(RunContext runContext, String key, BufferState state) throws IOException {
        KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        byte[] bytes = MAPPER.writeValueAsBytes(state);
        kv.put(key, new KVValueAndMetadata(new KVMetadata("batch webhook event buffer", (Duration) null), bytes));
    }

    private void deleteBuffer(RunContext runContext, String key) throws IOException {
        KVStore kv = runContext.namespaceKv(runContext.flowInfo().namespace());
        kv.delete(key);
    }

    private static Optional<Object> tryMap(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<Map<String, Object>>() {
            }));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> tryArray(String body) {
        try {
            return Optional.of(MAPPER.readValue(body, new TypeReference<List<Object>>() {
            }));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /**
     * One buffered webhook event, persisted in the KV buffer and later written as an ION row.
     */
    public record BufferedEvent(
        String eventId,
        Instant eventReceived,
        Object body,
        Map<String, List<String>> headers,
        Map<String, List<String>> parameters
    ) {
        Map<String, Object> toRow() {
            Map<String, Object> row = new HashMap<>();
            row.put("eventId", eventId);
            row.put("eventReceived", eventReceived != null ? eventReceived.toString() : null);
            row.put("body", body);
            row.put("headers", headers);
            row.put("parameters", parameters);
            return row;
        }
    }

    /**
     * Durable buffer state stored in the namespace KV store.
     */
    public record BufferState(List<BufferedEvent> events, Instant firstEventAt) {
        public BufferState {
            events = events != null ? List.copyOf(events) : List.of();
        }

        static BufferState empty() {
            return new BufferState(List.of(), null);
        }
    }

    /**
     * Lightweight output used only while probing {@code when}/conditions for a single event.
     */
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    static class EventProbeOutput implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Request body of the webhook event (parsed JSON when possible).")
        private Object body;

        @Schema(title = "Request headers of the webhook event.")
        private Map<String, List<String>> headers;

        @Schema(title = "Query parameters of the webhook event.")
        private Map<String, List<String>> parameters;
    }

    @Builder
    @ToString
    @EqualsAndHashCode
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "URI of the ION file containing one row per buffered event.")
        @NotNull
        private URI uri;

        @Schema(title = "Number of events included in this batch.")
        @NotNull
        private Integer count;
    }
}
