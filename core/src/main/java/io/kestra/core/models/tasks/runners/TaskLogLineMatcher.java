package io.kestra.core.models.tasks.runners;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.models.tasks.runners.otlp.OtlpRecord;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AssetEmit;
import io.kestra.core.runners.AssetEmitter;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Singleton;

import static io.kestra.core.runners.RunContextLogger.ORIGINAL_TIMESTAMP_KEY;

/**
 * Service for matching and capturing structured data from task execution logs.
 * <p>
 * Example log formats that may be matched:
 *
 * <pre>{@code
 * ::{"outputs":{"key":"value"}}::
 * ::{"otlp":{"resourceLogs":[...]}}::
 * }</pre>
 *
 * The {@code otlp} form carries an OpenTelemetry OTLP/JSON record as framed by the kotlp process
 * wrapper; see {@link OtlpRecord}.
 */
@Singleton
public class TaskLogLineMatcher {

    protected static final Pattern LOG_DATA_SYNTAX = Pattern.compile("^::(\\{.*})::$");

    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    /**
     * Attempts to match and extract structured data from a given log line.
     * <p>
     * If the line contains recognized patterns (e.g., JSON-encoded output markers),
     * a {@link TaskLogMatch} is returned encapsulating the extracted data.
     * </p>
     *
     * @param logLine the raw log line.
     * @param logger the logger
     * @param runContext the {@link RunContext}
     * @return an {@link Optional} containing the {@link TaskLogMatch} if a match was found,
     *         otherwise {@link Optional#empty()}
     */
    public Optional<TaskLogMatch> matches(String logLine, Logger logger, RunContext runContext, Instant instant) throws IOException {
        Optional<String> matches = matches(logLine);
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        TaskLogMatch match = MAPPER.readValue(matches.get(), TaskLogLineMatcher.TaskLogMatch.class);

        return Optional.of(handle(logger, runContext, instant, match, matches.get()));
    }

    protected TaskLogMatch handle(Logger logger, RunContext runContext, Instant instant, TaskLogMatch match, String data) {

        if (match.metrics() != null) {
            match.metrics().forEach(runContext::metric);
        }

        if (match.logs() != null) {
            match.logs().forEach(it ->
            {
                try {
                    LoggingEventBuilder builder = runContext
                        .logger()
                        .atLevel(it.level())
                        .addKeyValue(ORIGINAL_TIMESTAMP_KEY, instant);
                    builder.log(it.message());
                } catch (Exception e) {
                    logger.warn("Invalid log '{}'", data, e);
                }
            });
        }

        if (match.assets() != null) {
            try {
                AssetEmitter assetEmitter = runContext.assets();
                assetEmitter.emit(match.assets());
            } catch (IllegalVariableEvaluationException e) {
                logger.warn("Unable to get asset emitter for log '{}'", data, e);
            } catch (QueueException e) {
                logger.warn("Unable to emit asset for log '{}'", data, e);
            }
        }

        if (match.otlp() != null && !match.otlp().isEmpty()) {
            handleOtlp(logger, runContext, instant, match.otlp(), data);
        }

        return match;
    }

    /**
     * Parses a bare OTLP/JSON NDJSON stream — one OTLP record per line, without the
     * {@code ::{...}::} framing — as produced by the
     * <a href="https://opentelemetry.io/docs/specs/otel/protocol/file-exporter/">OTLP File Exporter</a>.
     * <p>
     * OTLP logs and metrics are forwarded to the {@link RunContext} exactly as for framed log
     * lines; OTLP traces are only parsed and returned. Blank lines and lines that cannot be
     * parsed (e.g. a line truncated by file rotation) are skipped with a warning. The given
     * stream is fully consumed and closed.
     *
     * @param inputStream the NDJSON stream, read as UTF-8
     * @param logger the logger used to report skipped lines
     * @param runContext the {@link RunContext} receiving logs and metrics
     * @param instant the fallback timestamp for log records without a {@code timeUnixNano}
     * @return every successfully parsed OTLP record, in stream order
     */
    public List<OtlpRecord> parseOtlp(InputStream inputStream, Logger logger, RunContext runContext, Instant instant) throws IOException {
        List<OtlpRecord> records = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                OtlpRecord record;
                try {
                    record = parseOtlpLine(line);
                } catch (JsonProcessingException e) {
                    logger.warn("Unable to parse OTLP record '{}'", line, e);
                    continue;
                }

                if (record.isEmpty()) {
                    logger.warn("Ignoring line without any OTLP section '{}'", line);
                    continue;
                }

                handleOtlp(logger, runContext, instant, record, line);
                records.add(record);
            }
        }

        return records;
    }

    /**
     * Parses a single bare OTLP/JSON record — one JSON object per line, without the
     * {@code ::{...}::} framing — as produced by the
     * <a href="https://opentelemetry.io/docs/specs/otel/protocol/file-exporter/">OTLP File Exporter</a>.
     * <p>
     * Unlike {@link #parseOtlp}, this does not forward the record to a {@link RunContext} — it only
     * parses it, leaving dispatch (e.g. to an {@link AbstractLogConsumer}) to the caller.
     *
     * @param line one line of bare OTLP/JSON
     * @return the parsed record
     */
    public OtlpRecord parseOtlpLine(String line) throws JsonProcessingException {
        return MAPPER.readValue(line, OtlpRecord.class);
    }

    /**
     * Forwards the logs and metrics of an OTLP record to the {@link RunContext}; traces are left
     * untouched as they are only exposed to the caller for now.
     */
    protected void handleOtlp(Logger logger, RunContext runContext, Instant instant, OtlpRecord record, String data) {
        ListUtils.emptyOnNull(record.resourceLogs()).stream()
            .flatMap(resourceLogs -> ListUtils.emptyOnNull(resourceLogs.scopeLogs()).stream())
            .flatMap(scopeLogs -> ListUtils.emptyOnNull(scopeLogs.logRecords()).stream())
            .forEach(logRecord ->
            {
                try {
                    runContext
                        .logger()
                        .atLevel(otlpSeverityToLevel(logRecord))
                        .addKeyValue(ORIGINAL_TIMESTAMP_KEY, toInstant(logRecord.timeUnixNano(), instant))
                        .log(logRecord.body() != null ? logRecord.body().asText() : null);
                } catch (Exception e) {
                    logger.warn("Invalid OTLP log '{}'", data, e);
                }
            });

        ListUtils.emptyOnNull(record.resourceMetrics()).stream()
            .flatMap(resourceMetrics -> ListUtils.emptyOnNull(resourceMetrics.scopeMetrics()).stream())
            .flatMap(scopeMetrics -> ListUtils.emptyOnNull(scopeMetrics.metrics()).stream())
            .forEach(metric ->
            {
                try {
                    toKestraMetrics(metric, instant).forEach(runContext::metric);
                } catch (Exception e) {
                    logger.warn("Invalid OTLP metric '{}'", data, e);
                }
            });
    }

    /**
     * Maps an OTLP severity to an SLF4J level: {@code severityNumber} first (per the OpenTelemetry
     * log data model), then {@code severityText}, defaulting to {@link Level#INFO}.
     */
    protected Level otlpSeverityToLevel(OtlpRecord.LogRecord logRecord) {
        Integer severityNumber = logRecord.severityNumber();
        if (severityNumber != null && severityNumber > 0) {
            if (severityNumber <= 4) {
                return Level.TRACE;
            }
            if (severityNumber <= 8) {
                return Level.DEBUG;
            }
            if (severityNumber <= 12) {
                return Level.INFO;
            }
            if (severityNumber <= 16) {
                return Level.WARN;
            }
            return Level.ERROR;
        }

        String severityText = logRecord.severityText();
        if (severityText != null && !severityText.isBlank()) {
            return switch (severityText.trim().toUpperCase()) {
                case "TRACE" -> Level.TRACE;
                case "DEBUG" -> Level.DEBUG;
                case "WARN", "WARNING" -> Level.WARN;
                case "ERROR", "FATAL" -> Level.ERROR;
                default -> Level.INFO;
            };
        }

        return Level.INFO;
    }

    /**
     * Converts an OTLP unix-nano timestamp to an {@link Instant}, falling back when it is absent.
     */
    public Instant toInstant(Long unixNano, Instant fallback) {
        return unixNano == null || unixNano == 0 ? fallback : Instant.ofEpochSecond(0, unixNano);
    }

    /**
     * Converts an OTLP metric into Kestra metric entries, one per data point.
     * <p>
     * Gauges and cumulative sums map to a Kestra {@link Gauge} (the last sample wins, so periodic
     * cumulative samples end up as the final total), while delta sums map to a Kestra
     * {@link Counter} (each sample increments). Data-point attributes become tags, and each
     * data point's {@code timeUnixNano} is forwarded as the metric timestamp, falling back to
     * {@code instant} when absent.
     */
    protected List<AbstractMetricEntry<?>> toKestraMetrics(OtlpRecord.Metric metric, Instant instant) {
        List<OtlpRecord.NumberDataPoint> dataPoints;
        boolean isDeltaSum = false;

        if (metric.gauge() != null) {
            dataPoints = ListUtils.emptyOnNull(metric.gauge().dataPoints());
        } else if (metric.sum() != null) {
            dataPoints = ListUtils.emptyOnNull(metric.sum().dataPoints());
            isDeltaSum = Integer.valueOf(1).equals(metric.sum().aggregationTemporality());
        } else {
            return List.of();
        }

        String description = otlpMetricDescription(metric);

        List<AbstractMetricEntry<?>> entries = new ArrayList<>(dataPoints.size());
        for (OtlpRecord.NumberDataPoint dataPoint : dataPoints) {
            Double value = dataPoint.asDouble() != null
                ? dataPoint.asDouble()
                : Optional.ofNullable(dataPoint.asInt()).map(Long::doubleValue).orElse(null);
            if (value == null) {
                continue;
            }

            String[] tags = otlpAttributesToTags(dataPoint.attributes());
            Instant timestamp = toInstant(dataPoint.timeUnixNano(), instant);
            entries.add(isDeltaSum
                ? Counter.of(metric.name(), description, value, timestamp, tags)
                : Gauge.of(metric.name(), description, value, timestamp, tags));
        }

        return entries;
    }

    private static String otlpMetricDescription(OtlpRecord.Metric metric) {
        boolean hasDescription = metric.description() != null && !metric.description().isBlank();
        boolean hasUnit = metric.unit() != null && !metric.unit().isBlank();
        if (hasDescription && hasUnit) {
            return "%s (%s)".formatted(metric.description(), metric.unit());
        }
        if (hasDescription) {
            return metric.description();
        }
        return hasUnit ? metric.unit() : null;
    }

    private static String[] otlpAttributesToTags(List<OtlpRecord.KeyValue> attributes) {
        return ListUtils.emptyOnNull(attributes).stream()
            .filter(attribute -> attribute.key() != null && attribute.value() != null && attribute.value().asText() != null)
            .flatMap(attribute -> Stream.of(attribute.key(), attribute.value().asText()))
            .toArray(String[]::new);
    }

    protected Optional<String> matches(String logLine) {
        Matcher m = LOG_DATA_SYNTAX.matcher(logLine);
        return m.find() ? Optional.ofNullable(m.group(1)) : Optional.empty();
    }

    /**
     * Represents the result of log line match.
     *
     * @param outputs a map of extracted output key-value pairs
     * @param metrics a list of captured metric entries, typically used for reporting or monitoring
     * @param logs additional log lines derived from the matched line, if any
     * @param assets assets emitted through the matched line, if any
     * @param otlp an OpenTelemetry record captured from the matched line, if any (the key spelling
     *             follows the kotlp {@code ::{"otlp":...}::} framing)
     */
    public record TaskLogMatch(
        Map<String, Object> outputs,
        List<AbstractMetricEntry<?>> metrics,
        List<LogLine> logs,
        AssetEmit assets,
        OtlpRecord otlp) {
        @Override
        public Map<String, Object> outputs() {
            return Optional.ofNullable(outputs).orElse(Map.of());
        }
    }

    public record LogLine(
        Level level,
        String message) {
    }
}
