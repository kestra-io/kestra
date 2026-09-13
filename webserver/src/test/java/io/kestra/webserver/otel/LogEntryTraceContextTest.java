package io.kestra.webserver.otel;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.kestra.core.models.executions.LogEntry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;

import static org.assertj.core.api.Assertions.assertThat;

class LogEntryTraceContextTest {
    @RegisterExtension
    static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

    @Test
    void shouldExposeActiveTraceContextInMdc() {
        // Given
        LogEntry logEntry = LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.tests")
            .flowId("trace")
            .executionId("exec-1")
            .build();
        Tracer tracer = otelTesting.getOpenTelemetry().getTracer("test");
        Span span = tracer.spanBuilder("test").startSpan();

        // When
        Map<String, String> withSpan;
        try (Scope ignored = span.makeCurrent()) {
            withSpan = logEntry.toMap();
        } finally {
            span.end();
        }
        Map<String, String> withoutSpan = logEntry.toMap();

        // Then
        assertThat(withSpan).containsEntry("trace_id", span.getSpanContext().getTraceId());
        assertThat(withSpan).containsEntry("span_id", span.getSpanContext().getSpanId());
        assertThat(withSpan).containsEntry("executionId", "exec-1");
        assertThat(withoutSpan).doesNotContainKey("trace_id");
        assertThat(withoutSpan).doesNotContainKey("span_id");
    }
}
