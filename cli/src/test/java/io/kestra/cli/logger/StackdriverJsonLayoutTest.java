package io.kestra.cli.logger;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

import static org.assertj.core.api.Assertions.assertThat;

class StackdriverJsonLayoutTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LoggerContext context = new LoggerContext();

    @Test
    void shouldSplitTimestampIntoSecondsAndNanosWhenFormatting() throws Exception {
        LoggingEvent event = event(Level.INFO, "hello", null);
        event.setTimeStamp(1_700_000_000_123L);

        Map<String, Object> json = render(layout(), event);

        assertThat(((Number) json.get("timestampSeconds")).longValue()).isEqualTo(1_700_000_000L);
        assertThat(((Number) json.get("timestampNanos")).longValue()).isEqualTo(123_000_000L);
    }

    @Test
    void shouldMapLevelToSeverityAndKeepLoggerAndMessageFields() throws Exception {
        LoggingEvent event = event(Level.WARN, "disk {} is full", null, Map.of(), "sda1");

        Map<String, Object> json = render(layout(), event);

        assertThat(json)
            .containsEntry("severity", "WARN")
            .containsEntry("logger", "io.kestra.test")
            .containsEntry("thread", "worker-1")
            .containsEntry("message", "disk sda1 is full")
            .doesNotContainKey("level");
    }

    @Test
    void shouldAppendStackTraceToMessageAndOmitExceptionFieldByDefault() throws Exception {
        LoggingEvent event = event(Level.ERROR, "task failed", new IllegalStateException("boom"));

        Map<String, Object> json = render(layout(), event);

        // Stackdriver groups an error with its stack trace only when both are in the message
        assertThat((String) json.get("message"))
            .startsWith("task failed\n")
            .contains("java.lang.IllegalStateException: boom")
            .contains("StackdriverJsonLayoutTest");
        assertThat(json).doesNotContainKey("exception");
    }

    @Test
    void shouldKeepMessageUnchangedWhenExceptionInMessageIsDisabled() throws Exception {
        StackdriverJsonLayout layout = layout();
        layout.setIncludeExceptionInMessage(false);
        layout.start();
        LoggingEvent event = event(Level.ERROR, "task failed", new IllegalStateException("boom"));

        Map<String, Object> json = render(layout, event);

        assertThat(json).containsEntry("message", "task failed");
    }

    @Test
    void shouldIncludeMdcAndCustomJsonWithoutOverridingEventFields() throws Exception {
        StackdriverJsonLayout layout = layout();
        layout.setCustomJson(Map.of("service", "kestra-worker", "severity", "DEBUG", "executionId", "from-custom"));
        LoggingEvent event = event(Level.INFO, "running", null, Map.of("executionId", "exec-42"));

        Map<String, Object> json = render(layout, event);

        assertThat(json)
            .containsEntry("service", "kestra-worker")
            .containsEntry("severity", "INFO")
            .containsEntry("executionId", "exec-42");
    }

    @Test
    void shouldWriteOneJsonDocumentPerLine() {
        LoggingEvent event = event(Level.ERROR, "multi\nline", new IllegalStateException("boom"));

        String line = layout().doLayout(event);

        // a JSON log collector reads one entry per line, so newlines in the message must be escaped
        assertThat(line).endsWith(System.lineSeparator());
        assertThat(line.strip()).doesNotContain("\n");
    }

    private StackdriverJsonLayout layout() {
        StackdriverJsonLayout layout = new StackdriverJsonLayout();
        layout.setContext(context);
        layout.start();
        return layout;
    }

    private LoggingEvent event(Level level, String message, Throwable throwable, Object... args) {
        return event(level, message, throwable, Map.of(), args);
    }

    private LoggingEvent event(Level level, String message, Throwable throwable, Map<String, String> mdc, Object... args) {
        Logger logger = context.getLogger("io.kestra.test");
        LoggingEvent event = new LoggingEvent(Logger.class.getName(), logger, level, message, throwable, args);
        event.setThreadName("worker-1");
        event.setMDCPropertyMap(mdc);
        return event;
    }

    private static Map<String, Object> render(StackdriverJsonLayout layout, LoggingEvent event) throws Exception {
        return MAPPER.readValue(layout.doLayout(event), new TypeReference<>() {});
    }
}
