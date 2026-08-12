package io.kestra.core.models.tasks.runners.otlp;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;

/**
 * A single OpenTelemetry OTLP/JSON record, as emitted by the
 * <a href="https://opentelemetry.io/docs/specs/otel/protocol/file-exporter/">OTLP File Exporter</a>
 * (one JSON object per line, holding one of {@code resourceLogs}, {@code resourceMetrics} or
 * {@code resourceSpans}).
 * <p>
 * Field names match the OTLP/JSON wire format; unsupported sections (e.g. histogram metrics) are
 * ignored by the lenient mapper used for parsing.
 *
 * @param resourceLogs the OTLP logs section, if any
 * @param resourceMetrics the OTLP metrics section, if any
 * @param resourceSpans the OTLP traces section, if any
 */
public record OtlpRecord(
    List<ResourceLogs> resourceLogs,
    List<ResourceMetrics> resourceMetrics,
    List<ResourceSpans> resourceSpans) {

    /**
     * @return true when the record contains none of the three OTLP sections.
     */
    public boolean isEmpty() {
        return ListUtils.isEmpty(resourceLogs) && ListUtils.isEmpty(resourceMetrics) && ListUtils.isEmpty(resourceSpans);
    }

    // --- logs ---

    public record ResourceLogs(
        Resource resource,
        List<ScopeLogs> scopeLogs,
        String schemaUrl) {
    }

    public record ScopeLogs(
        Scope scope,
        List<LogRecord> logRecords,
        String schemaUrl) {
    }

    public record LogRecord(
        Long timeUnixNano,
        Long observedTimeUnixNano,
        Integer severityNumber,
        String severityText,
        AnyValue body,
        List<KeyValue> attributes,
        String traceId,
        String spanId) {
    }

    // --- metrics ---

    public record ResourceMetrics(
        Resource resource,
        List<ScopeMetrics> scopeMetrics,
        String schemaUrl) {
    }

    public record ScopeMetrics(
        Scope scope,
        List<Metric> metrics,
        String schemaUrl) {
    }

    public record Metric(
        String name,
        String description,
        String unit,
        GaugeData gauge,
        SumData sum) {
    }

    public record GaugeData(List<NumberDataPoint> dataPoints) {
    }

    /**
     * An OTLP sum metric.
     *
     * @param aggregationTemporality 1 for DELTA, 2 for CUMULATIVE
     */
    public record SumData(
        List<NumberDataPoint> dataPoints,
        Integer aggregationTemporality,
        Boolean isMonotonic) {
    }

    public record NumberDataPoint(
        Long startTimeUnixNano,
        Long timeUnixNano,
        Double asDouble,
        Long asInt,
        List<KeyValue> attributes) {
    }

    // --- traces ---

    public record ResourceSpans(
        Resource resource,
        List<ScopeSpans> scopeSpans,
        String schemaUrl) {
    }

    public record ScopeSpans(
        Scope scope,
        List<Span> spans,
        String schemaUrl) {
    }

    public record Span(
        String traceId,
        String spanId,
        String parentSpanId,
        String name,
        Integer kind,
        Long startTimeUnixNano,
        Long endTimeUnixNano,
        List<KeyValue> attributes,
        List<SpanEvent> events,
        List<SpanLink> links,
        SpanStatus status) {
    }

    public record SpanEvent(
        Long timeUnixNano,
        String name,
        List<KeyValue> attributes) {
    }

    public record SpanLink(
        String traceId,
        String spanId,
        List<KeyValue> attributes) {
    }

    /**
     * The OTLP span status.
     *
     * @param code 0 for UNSET, 1 for OK, 2 for ERROR
     */
    public record SpanStatus(
        Integer code,
        String message) {
    }

    // --- shared ---

    public record Resource(List<KeyValue> attributes) {
    }

    public record Scope(
        String name,
        String version,
        List<KeyValue> attributes) {
    }

    public record KeyValue(
        String key,
        AnyValue value) {
    }

    /**
     * The OTLP {@code AnyValue} union: exactly one branch is expected to be non-null.
     */
    public record AnyValue(
        String stringValue,
        Boolean boolValue,
        Long intValue,
        Double doubleValue,
        ArrayValue arrayValue,
        KvlistValue kvlistValue,
        String bytesValue) {

        /**
         * @return the first non-null branch rendered as text (array and kvlist branches are
         *         serialized to JSON), or null when every branch is null.
         */
        public String asText() {
            if (stringValue != null) {
                return stringValue;
            }
            if (boolValue != null) {
                return boolValue.toString();
            }
            if (intValue != null) {
                return intValue.toString();
            }
            if (doubleValue != null) {
                return doubleValue.toString();
            }
            if (bytesValue != null) {
                return bytesValue;
            }
            try {
                if (arrayValue != null) {
                    return JacksonMapper.ofJson().writeValueAsString(arrayValue);
                }
                if (kvlistValue != null) {
                    return JacksonMapper.ofJson().writeValueAsString(kvlistValue);
                }
            } catch (JsonProcessingException e) {
                throw new KestraRuntimeException(e);
            }
            return null;
        }
    }

    public record ArrayValue(List<AnyValue> values) {
    }

    public record KvlistValue(List<KeyValue> values) {
    }
}
