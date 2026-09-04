package io.kestra.core.models.tasks.runners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import io.kestra.core.models.tasks.runners.otlp.OtlpRecord;
import io.kestra.core.runners.RunContext;
import io.kestra.core.trace.propagation.RunContextTextMapGetter;
import io.kestra.core.utils.ListUtils;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class OtlpSpanForwarder {
    @Inject
    private Optional<OpenTelemetry> openTelemetry;

    public void forward(List<OtlpRecord.ResourceSpans> resourceSpans, RunContext runContext, Logger logger) {
        if (resourceSpans == null || resourceSpans.isEmpty() || openTelemetry.isEmpty()) {
            return;
        }

        // Extract the Kestra RunContext's trace context to serve as the parent.
        var propagator = openTelemetry.get().getPropagators().getTextMapPropagator();
        Context taskRunContext = propagator.extract(Context.current(), runContext, RunContextTextMapGetter.INSTANCE);

        Map<String, Context> contextBySpanId = new HashMap<>();

        for (OtlpRecord.ResourceSpans resourceSpan : resourceSpans) {
            Attributes resourceAttributes = toAttributes(resourceSpan.resource() != null ? resourceSpan.resource().attributes() : null);

            for (OtlpRecord.ScopeSpans scopeSpan : ListUtils.emptyOnNull(resourceSpan.scopeSpans())) {
                String scopeName = scopeSpan.scope() != null && scopeSpan.scope().name() != null ? scopeSpan.scope().name() : "unknown";
                String scopeVersion = scopeSpan.scope() != null ? scopeSpan.scope().version() : null;
                Tracer scopeTracer = openTelemetry.get().getTracer(scopeName, scopeVersion);

                for (OtlpRecord.Span span : ListUtils.emptyOnNull(scopeSpan.spans())) {
                    try {
                        Context parentContext = determineParentContext(span, taskRunContext, contextBySpanId, logger);

                        SpanBuilder spanBuilder = scopeTracer.spanBuilder(span.name() != null ? span.name() : "unknown")
                            .setParent(parentContext);

                        if (span.kind() != null) {
                            spanBuilder.setSpanKind(mapSpanKind(span.kind()));
                        }

                        if (span.startTimeUnixNano() != null) {
                            spanBuilder.setStartTimestamp(span.startTimeUnixNano(), TimeUnit.NANOSECONDS);
                        }

                        AttributesBuilder attributesBuilder = Attributes.builder().putAll(resourceAttributes);
                        if (span.attributes() != null) {
                            attributesBuilder.putAll(toAttributes(span.attributes()));
                        }
                        spanBuilder.setAllAttributes(attributesBuilder.build());

                        if (span.links() != null) {
                            for (OtlpRecord.SpanLink link : span.links()) {
                                // We cannot easily map links since they rely on SpanContext which is constructed from traceId/spanId.
                                // It requires recreating SpanContext, but we can do that since SpanContext.create(...) takes strings.
                                if (link.traceId() != null && link.spanId() != null) {
                                    io.opentelemetry.api.trace.SpanContext linkContext = io.opentelemetry.api.trace.SpanContext.create(
                                        link.traceId(),
                                        link.spanId(),
                                        io.opentelemetry.api.trace.TraceFlags.getDefault(),
                                        io.opentelemetry.api.trace.TraceState.getDefault()
                                    );
                                    spanBuilder.addLink(linkContext, toAttributes(link.attributes()));
                                }
                            }
                        }

                        Span newSpan = spanBuilder.startSpan();

                        try (var scope = newSpan.makeCurrent()) {
                            if (span.spanId() != null) {
                                contextBySpanId.put(span.spanId(), Context.current());
                            }

                            if (span.events() != null) {
                                for (OtlpRecord.SpanEvent event : span.events()) {
                                    String eventName = event.name() != null ? event.name() : "unknown";
                                    Attributes eventAttributes = toAttributes(event.attributes());
                                    if (event.timeUnixNano() != null) {
                                        newSpan.addEvent(eventName, eventAttributes, event.timeUnixNano(), TimeUnit.NANOSECONDS);
                                    } else {
                                        newSpan.addEvent(eventName, eventAttributes);
                                    }
                                }
                            }

                            if (span.status() != null && span.status().code() != null) {
                                newSpan.setStatus(mapStatusCode(span.status().code()), span.status().message());
                            }
                        } finally {
                            if (span.endTimeUnixNano() != null) {
                                newSpan.end(span.endTimeUnixNano(), TimeUnit.NANOSECONDS);
                            } else {
                                newSpan.end();
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to forward span '{}'", span.name(), e);
                    }
                }
            }
        }
    }

    private Context determineParentContext(OtlpRecord.Span span, Context taskRunContext, Map<String, Context> contextBySpanId, Logger logger) {
        if (span.parentSpanId() == null || span.parentSpanId().isBlank()) {
            return taskRunContext;
        }

        if (span.parentSpanId() != null && !span.parentSpanId().isEmpty()) {
            Context parentContext = contextBySpanId.get(span.parentSpanId());
            if (parentContext != null) {
                return parentContext;
            } else {
                logger.warn("Parent span id {} not found for span {}, attaching to task run context", span.parentSpanId(), span.name());
                return taskRunContext;
            }
        }

        return taskRunContext;
    }

    private SpanKind mapSpanKind(int kind) {
        return switch (kind) {
            case 1 -> SpanKind.INTERNAL;
            case 2 -> SpanKind.SERVER;
            case 3 -> SpanKind.CLIENT;
            case 4 -> SpanKind.PRODUCER;
            case 5 -> SpanKind.CONSUMER;
            default -> SpanKind.INTERNAL;
        };
    }

    private StatusCode mapStatusCode(int code) {
        return switch (code) {
            case 1 -> StatusCode.OK;
            case 2 -> StatusCode.ERROR;
            default -> StatusCode.UNSET;
        };
    }

    private Attributes toAttributes(List<OtlpRecord.KeyValue> keyValues) {
        if (keyValues == null || keyValues.isEmpty()) {
            return Attributes.empty();
        }
        AttributesBuilder builder = Attributes.builder();
        for (OtlpRecord.KeyValue kv : keyValues) {
            if (kv.key() != null && kv.value() != null) {
                String value = kv.value().asText();
                if (value != null) {
                    builder.put(kv.key(), value);
                }
            }
        }
        return builder.build();
    }
}
