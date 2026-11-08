package io.kestra.core.trace.propagation;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropagationTest {
    @Test
    void shouldReturnEmptyWhenOpenTelemetryIsEmpty() {
        Optional<String> traceParent = TracePropagation.traceParent(Optional.empty(), Context.current());
        assertThat(traceParent).isEmpty();
    }

    @Test
    void shouldExtractTraceParentFromContext() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();

        Tracer tracer = openTelemetry.getTracer("test");
        Span span = tracer.spanBuilder("test-span").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            Optional<String> traceParent = TracePropagation.traceParent(Optional.of(openTelemetry), Context.current());
            assertThat(traceParent).isPresent();
            assertThat(traceParent.get()).startsWith("00-" + span.getSpanContext().getTraceId() + "-" + span.getSpanContext().getSpanId());
        } finally {
            span.end();
        }
    }
}
