package io.kestra.core.models.tasks.runners;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.queues.QueueException;
import io.kestra.core.runners.AssetEmitter;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.kestra.core.runners.RunContextLogger.ORIGINAL_TIMESTAMP_KEY;
import static io.kestra.core.utils.Rethrow.throwConsumer;

/**
 * Service for matching and capturing structured data from task execution logs.
 * <p>
 * Example log format that may be matched:
 * <pre>{@code
 * ::{"outputs":{"key":"value"}}::
 * }</pre>
 */
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
     * @param logLine    the raw log line.
     * @param logger     the logger
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
            match.logs().forEach(it -> {
                try {
                    LoggingEventBuilder builder = runContext
                        .logger()
                        .atLevel(it.level())
                        .addKeyValue(ORIGINAL_TIMESTAMP_KEY, instant);
                    builder.log(it.message());
                } catch (Exception e) {
                    logger.warn("Invalid log '{}'",data, e);
                }
            });
        }

        if (match.assets() != null) {
            try {
                AssetEmitter assetEmitter = runContext.assets();
                match.assets().forEach(throwConsumer(assetEmitter::upsert));
            } catch (IllegalVariableEvaluationException e) {
                logger.warn("Unable to get asset emitter for log '{}'", data, e);
            } catch (QueueException e) {
                logger.warn("Unable to emit asset for log '{}'", data, e);
            }
        }

        return match;
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
     * @param logs    additional log lines derived from the matched line, if any
     */
    public record TaskLogMatch(
        Map<String, Object> outputs,
        List<AbstractMetricEntry<?>> metrics,
        List<LogLine> logs,
        List<Asset> assets
        ) {
        @Override
        public Map<String, Object> outputs() {
            return Optional.ofNullable(outputs).orElse(Map.of());
        }
    }

    public record LogLine(
        Level level,
        String message
    ) {
    }
}
