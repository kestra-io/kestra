package io.kestra.core.models.tasks.runners;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.models.tasks.runners.TaskLogLineMatcher.TaskLogMatch;
import io.kestra.core.models.tasks.runners.otlp.OtlpRecord;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.utils.IdUtils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class TaskLogLineMatcherTest {
    private static final Instant FALLBACK_INSTANT = Instant.parse("2024-06-18T00:00:00Z");
    private static final long TIME_UNIX_NANO = 1_718_700_000_000_000_000L;
    // the stdout record of the kotlp recording in tasks/otlp/kotlp.ndjson
    private static final long RECORDED_STDOUT_UNIX_NANO = 1_786_530_207_904_629_286L;

    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private TaskLogLineMatcher matcher;

    @Test
    void shouldForwardOtlpLogWhenFramedLineContainsResourceLogs() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        Optional<TaskLogMatch> match = matcher.matches(
            framed(logRecord("{\"timeUnixNano\":\"" + TIME_UNIX_NANO + "\",\"severityNumber\":9,\"severityText\":\"INFO\",\"body\":{\"stringValue\":\"hello from kotlp\"},\"attributes\":[{\"key\":\"log.iostream\",\"value\":{\"stringValue\":\"stdout\"}}]}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(match).isPresent();
        assertThat(match.get().otlp().resourceLogs()).hasSize(1);
        assertThat(listAppender.list).hasSize(1);
        ILoggingEvent event = listAppender.list.getFirst();
        assertThat(event.getFormattedMessage()).isEqualTo("hello from kotlp");
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(originalTimestamp(event)).isEqualTo(Instant.ofEpochSecond(0, TIME_UNIX_NANO));
    }

    @Test
    void shouldMapSeverityNumberToSlf4jLevel() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        Map<Integer, Level> expected = Map.of(
            1, Level.TRACE,
            5, Level.DEBUG,
            9, Level.INFO,
            13, Level.WARN,
            17, Level.ERROR,
            24, Level.ERROR
        );
        for (Integer severityNumber : expected.keySet()) {
            matcher.matches(
                framed(logRecord("{\"severityNumber\":" + severityNumber + ",\"body\":{\"stringValue\":\"m-" + severityNumber + "\"}}")),
                runContext.logger(),
                runContext,
                FALLBACK_INSTANT
            );
        }

        expected.forEach((severityNumber, level) ->
            assertThat(listAppender.list).anySatisfy(event ->
            {
                assertThat(event.getFormattedMessage()).isEqualTo("m-" + severityNumber);
                assertThat(event.getLevel()).isEqualTo(level);
            })
        );
    }

    @Test
    void shouldFallbackToSeverityTextWhenSeverityNumberMissing() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        Map<String, Level> expected = Map.of(
            "WARN", Level.WARN,
            "error", Level.ERROR,
            "FATAL", Level.ERROR,
            "debug", Level.DEBUG
        );
        for (String severityText : expected.keySet()) {
            matcher.matches(
                framed(logRecord("{\"severityText\":\"" + severityText + "\",\"body\":{\"stringValue\":\"m-" + severityText + "\"}}")),
                runContext.logger(),
                runContext,
                FALLBACK_INSTANT
            );
        }

        expected.forEach((severityText, level) ->
            assertThat(listAppender.list).anySatisfy(event ->
            {
                assertThat(event.getFormattedMessage()).isEqualTo("m-" + severityText);
                assertThat(event.getLevel()).isEqualTo(level);
            })
        );
    }

    @Test
    void shouldDefaultToInfoWhenSeverityAbsent() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        matcher.matches(
            framed(logRecord("{\"body\":{\"stringValue\":\"no severity\"}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void shouldFallbackToProvidedInstantWhenTimeUnixNanoMissing() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        matcher.matches(
            framed(logRecord("{\"severityNumber\":9,\"body\":{\"stringValue\":\"no timestamp\"}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(listAppender.list).hasSize(1);
        assertThat(originalTimestamp(listAppender.list.getFirst())).isEqualTo(FALLBACK_INSTANT);
    }

    @Test
    void shouldRenderNonStringBodyWhenBodyIsNotString() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        matcher.matches(
            framed(logRecord("{\"severityNumber\":9,\"body\":{\"intValue\":\"42\"}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );
        matcher.matches(
            framed(logRecord("{\"severityNumber\":9,\"body\":{\"kvlistValue\":{\"values\":[{\"key\":\"a\",\"value\":{\"stringValue\":\"b\"}}]}}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(listAppender.list).hasSize(2);
        assertThat(listAppender.list.getFirst().getFormattedMessage()).isEqualTo("42");
        assertThat(listAppender.list.get(1).getFormattedMessage()).contains("\"key\":\"a\"").contains("\"stringValue\":\"b\"");
    }

    @Test
    void shouldConvertOtlpGaugeToKestraGauge() throws IOException {
        var runContext = runContext();

        matcher.matches(
            framed(metric("{\"name\":\"process.memory.usage\",\"description\":\"Resident set size\",\"unit\":\"By\",\"gauge\":{\"dataPoints\":[{\"timeUnixNano\":\"" + TIME_UNIX_NANO + "\",\"asInt\":\"1048576\"}]}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.getFirst()).isInstanceOf(Gauge.class);
        Gauge gauge = (Gauge) metrics.getFirst();
        assertThat(gauge.getName()).isEqualTo("process.memory.usage");
        assertThat(gauge.getValue()).isEqualTo(1048576d);
        assertThat(gauge.getDescription()).isEqualTo("Resident set size (By)");
        assertThat(gauge.getTimestamp()).isEqualTo(Instant.ofEpochSecond(0, TIME_UNIX_NANO));
    }

    @Test
    void shouldFallBackToInstantWhenMetricDataPointHasNoTimestamp() throws IOException {
        var runContext = runContext();

        matcher.matches(
            framed(metric("{\"name\":\"process.cpu.utilization\",\"gauge\":{\"dataPoints\":[{\"asDouble\":0.5}]}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.getFirst().getTimestamp()).isEqualTo(FALLBACK_INSTANT);
    }

    @Test
    void shouldKeepLastSampleWhenSumIsCumulative() throws IOException {
        var runContext = runContext();

        matcher.matches(
            framed(metric(cumulativeSum("process.cpu.time", 1.5))),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );
        matcher.matches(
            framed(metric(cumulativeSum("process.cpu.time", 3.5))),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.getFirst()).isInstanceOf(Gauge.class);
        assertThat(metrics.getFirst().getValue()).isEqualTo(3.5);
    }

    @Test
    void shouldAccumulateWhenSumIsDelta() throws IOException {
        var runContext = runContext();

        matcher.matches(
            framed(metric("{\"name\":\"requests\",\"sum\":{\"aggregationTemporality\":1,\"dataPoints\":[{\"asDouble\":2.0}]}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );
        matcher.matches(
            framed(metric("{\"name\":\"requests\",\"sum\":{\"aggregationTemporality\":1,\"dataPoints\":[{\"asDouble\":3.0}]}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(1);
        assertThat(metrics.getFirst()).isInstanceOf(Counter.class);
        assertThat(metrics.getFirst().getValue()).isEqualTo(5.0);
    }

    @Test
    void shouldFlattenDataPointAttributesToTags() throws IOException {
        var runContext = runContext();

        matcher.matches(
            framed(metric("{\"name\":\"process.cpu.utilization\",\"gauge\":{\"dataPoints\":[" +
                "{\"asDouble\":0.75,\"attributes\":[{\"key\":\"cpu.mode\",\"value\":{\"stringValue\":\"user\"}}]}," +
                "{\"asDouble\":0.25,\"attributes\":[{\"key\":\"cpu.mode\",\"value\":{\"stringValue\":\"system\"}},{\"key\":\"cpu.count\",\"value\":{\"intValue\":\"8\"}}]}" +
                "]}}")),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(2);
        assertThat(metrics).anySatisfy(metric ->
        {
            assertThat(metric.getTags()).containsEntry("cpu.mode", "user");
            assertThat(metric.getValue()).isEqualTo(0.75);
        });
        assertThat(metrics).anySatisfy(metric ->
        {
            assertThat(metric.getTags()).containsEntry("cpu.mode", "system").containsEntry("cpu.count", "8");
            assertThat(metric.getValue()).isEqualTo(0.25);
        });
    }

    @Test
    void shouldExposeSpansWithoutForwardingWhenFramedLineContainsResourceSpans() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        Optional<TaskLogMatch> match = matcher.matches(
            framed("{\"resourceSpans\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\"my-program\"}}]},\"scopeSpans\":[{\"scope\":{\"name\":\"kotlp\",\"version\":\"0.1.0\"},\"spans\":[" +
                "{\"traceId\":\"0af7651916cd43dd8448eb211c80319c\",\"spanId\":\"b7ad6b7169203331\",\"name\":\"exec my-program\",\"kind\":1,\"startTimeUnixNano\":\"" + TIME_UNIX_NANO + "\",\"endTimeUnixNano\":\"" + (TIME_UNIX_NANO + 2_000_000_000L) + "\",\"attributes\":[{\"key\":\"process.exit.code\",\"value\":{\"intValue\":\"3\"}}],\"status\":{\"code\":2,\"message\":\"exited with code 3\"}}," +
                "{\"traceId\":\"0af7651916cd43dd8448eb211c80319c\",\"spanId\":\"c8be7c8270314442\",\"parentSpanId\":\"b7ad6b7169203331\",\"name\":\"child\",\"kind\":1,\"events\":[{\"timeUnixNano\":\"" + TIME_UNIX_NANO + "\",\"name\":\"started\"}]}" +
                "]}]}]}"),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(match).isPresent();
        List<OtlpRecord.ResourceSpans> resourceSpans = match.get().otlp().resourceSpans();
        assertThat(resourceSpans).hasSize(1);
        List<OtlpRecord.Span> spans = resourceSpans.getFirst().scopeSpans().getFirst().spans();
        assertThat(spans).hasSize(2);
        assertThat(spans.getFirst().traceId()).isEqualTo("0af7651916cd43dd8448eb211c80319c");
        assertThat(spans.getFirst().name()).isEqualTo("exec my-program");
        assertThat(spans.getFirst().startTimeUnixNano()).isEqualTo(TIME_UNIX_NANO);
        assertThat(spans.getFirst().status().code()).isEqualTo(2);
        assertThat(spans.getFirst().attributes().getFirst().value().asText()).isEqualTo("3");
        assertThat(spans.get(1).parentSpanId()).isEqualTo("b7ad6b7169203331");
        assertThat(spans.get(1).events().getFirst().name()).isEqualTo("started");

        assertThat(listAppender.list).isEmpty();
        assertThat(runContext.metrics()).isEmpty();
    }

    /**
     * The fixture is a verbatim {@code --log-dir} recording of the kotlp release this build embeds,
     * invoked as
     * <pre>{@code
     * kotlp --log-dir . --interval 800 --service-name my-program -- \
     *   /bin/sh -c 'echo hello from file; dd if=/dev/zero of=payload.bin bs=1M count=64 2>/dev/null; sync; sleep 1; echo something went wrong >&2'
     * }</pre>
     * with only {@code host.name} replaced, followed by three lines the parser must skip: a blank
     * one, one truncated mid-object as file rotation would leave it, and a well-formed JSON object
     * holding no OTLP section. Those three are appended rather than recorded — kotlp does not
     * produce them on purpose.
     */
    @Test
    void shouldParseBareNdjsonStreamWhenGivenKotlpLogDirFile() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        List<OtlpRecord> records;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("tasks/otlp/kotlp.ndjson")) {
            records = matcher.parseOtlp(inputStream, runContext.logger(), runContext, FALLBACK_INSTANT);
        }

        // blank, truncated and empty-object lines are skipped
        assertThat(records).hasSize(6);
        assertThat(records.getFirst().resourceLogs()).isNotEmpty();
        assertThat(records.get(1).resourceMetrics()).isNotEmpty();
        assertThat(records.get(2).resourceMetrics()).isNotEmpty();
        assertThat(records.get(3).resourceLogs()).isNotEmpty();
        assertThat(records.get(4).resourceMetrics()).isNotEmpty();
        assertThat(records.get(5).resourceSpans()).isNotEmpty();

        // the recording really is kotlp's own
        assertThat(records.getFirst().resourceLogs().getFirst().resource().attributes())
            .anySatisfy(attribute ->
            {
                assertThat(attribute.key()).isEqualTo("telemetry.sdk.name");
                assertThat(attribute.value().asText()).isEqualTo("kotlp");
            });

        assertThat(listAppender.list).anySatisfy(event ->
        {
            assertThat(event.getFormattedMessage()).isEqualTo("hello from file");
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(originalTimestamp(event)).isEqualTo(Instant.ofEpochSecond(0, RECORDED_STDOUT_UNIX_NANO));
        });
        assertThat(listAppender.list).anySatisfy(event ->
        {
            assertThat(event.getFormattedMessage()).isEqualTo("something went wrong");
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
        });
        assertThat(listAppender.list).anySatisfy(event ->
        {
            assertThat(event.getFormattedMessage()).startsWith("Unable to parse OTLP record");
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
        });
        assertThat(listAppender.list).anySatisfy(event ->
        {
            assertThat(event.getFormattedMessage()).startsWith("Ignoring line without any OTLP section");
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
        });

        // one entry per (name, tags) pair over the three metric batches, each holding the last sample
        List<AbstractMetricEntry<?>> metrics = runContext.metrics();
        assertThat(metrics).hasSize(10);
        assertThat(metricNamed(metrics, "process.memory.usage", Map.of()).getValue()).isEqualTo(9_134_080d);
        assertThat(metricNamed(metrics, "process.thread.count", Map.of()).getValue()).isEqualTo(2d);
        assertThat(metricNamed(metrics, "process.cpu.time", Map.of("cpu.mode", "system")).getValue()).isEqualTo(0.029441);
        // the closing batch is derived from rusage: kotlp carries the last /proc read of an IO
        // counter it cannot recompute rather than restarting the series at zero
        assertThat(metricNamed(metrics, "process.disk.io", Map.of("disk.io.direction", "write")).getValue()).isEqualTo(81_788_928d);
        assertThat(metricNamed(metrics, "process.disk.io", Map.of("disk.io.direction", "read")).getValue()).isEqualTo(73_728d);
    }

    @Test
    void shouldIgnoreUnknownOtlpFieldsWhenParsing() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        String ndjson = "{\"resourceMetrics\":[{\"scopeMetrics\":[{\"metrics\":[{\"name\":\"latency\",\"histogram\":{\"dataPoints\":[{\"count\":\"5\"}]}}]}]}],\"unknownTopLevelField\":true}\n";
        List<OtlpRecord> records = matcher.parseOtlp(
            new ByteArrayInputStream(ndjson.getBytes(StandardCharsets.UTF_8)),
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        // the record parses, but a histogram metric has no Kestra equivalent
        assertThat(records).hasSize(1);
        assertThat(runContext.metrics()).isEmpty();
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void shouldStillParseOutputsWhenNoOtlpKey() throws IOException {
        var runContext = runContext();

        Optional<TaskLogMatch> match = matcher.matches(
            "::{\"outputs\":{\"key\":\"value\"}}::",
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(match).isPresent();
        assertThat(match.get().outputs()).containsEntry("key", "value");
        assertThat(match.get().otlp()).isNull();
    }

    @Test
    void shouldProcessOutputsAndOtlpWhenFramedLineContainsBoth() throws IOException {
        var runContext = runContext();
        var listAppender = appender(runContext);

        Optional<TaskLogMatch> match = matcher.matches(
            "::{\"outputs\":{\"key\":\"value\"},\"otlp\":" + logRecord("{\"severityNumber\":9,\"body\":{\"stringValue\":\"combined\"}}") + "}::",
            runContext.logger(),
            runContext,
            FALLBACK_INSTANT
        );

        assertThat(match).isPresent();
        assertThat(match.get().outputs()).containsEntry("key", "value");
        assertThat(listAppender.list).hasSize(1);
        assertThat(listAppender.list.getFirst().getFormattedMessage()).isEqualTo("combined");
    }

    private RunContext runContext() {
        return runContextFactory.of("id", "namespace", IdUtils.create());
    }

    private ListAppender<ILoggingEvent> appender(RunContext runContext) {
        var listAppender = new ListAppender<ILoggingEvent>();
        listAppender.start();
        var logger = (ch.qos.logback.classic.Logger) runContext.logger();
        logger.setLevel(Level.TRACE);
        logger.addAppender(listAppender);
        return listAppender;
    }

    private static AbstractMetricEntry<?> metricNamed(List<AbstractMetricEntry<?>> metrics, String name, Map<String, String> tags) {
        return metrics.stream()
            .filter(metric -> name.equals(metric.getName()) && tags.equals(metric.getTags()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No metric '%s' with tags %s among %s".formatted(name, tags, metrics)));
    }

    private static Instant originalTimestamp(ILoggingEvent event) {
        return event.getKeyValuePairs().stream()
            .filter(kv -> RunContextLogger.ORIGINAL_TIMESTAMP_KEY.equals(kv.key))
            .map(kv -> (Instant) kv.value)
            .findFirst()
            .orElse(null);
    }

    private static String framed(String otlpJson) {
        return "::{\"otlp\":%s}::".formatted(otlpJson);
    }

    private static String logRecord(String logRecordJson) {
        return "{\"resourceLogs\":[{\"scopeLogs\":[{\"scope\":{\"name\":\"kotlp\",\"version\":\"0.1.0\"},\"logRecords\":[%s]}]}]}".formatted(logRecordJson);
    }

    private static String metric(String metricJson) {
        return "{\"resourceMetrics\":[{\"scopeMetrics\":[{\"scope\":{\"name\":\"kotlp\",\"version\":\"0.1.0\"},\"metrics\":[%s]}]}]}".formatted(metricJson);
    }

    private static String cumulativeSum(String name, double value) {
        return "{\"name\":\"%s\",\"unit\":\"s\",\"sum\":{\"aggregationTemporality\":2,\"isMonotonic\":true,\"dataPoints\":[{\"startTimeUnixNano\":\"%d\",\"timeUnixNano\":\"%d\",\"asDouble\":%s}]}}"
            .formatted(name, TIME_UNIX_NANO, TIME_UNIX_NANO, value);
    }
}
