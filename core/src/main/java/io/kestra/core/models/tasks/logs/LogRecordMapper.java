package io.kestra.core.models.tasks.logs;

import java.time.Instant;

import io.kestra.core.models.executions.LogEntry;

public final class LogRecordMapper {

    private LogRecordMapper() {
    }

    public static LogRecord mapToLogRecord(LogEntry log) {
        return mapToLogRecord(log, null);
    }

    public static LogRecord mapToLogRecord(LogEntry log, Integer maxMessageSize) {
        return LogRecord.builder()
            .resource("Kestra")
            .timestampEpochNanos(instantInNanos(log.getTimestamp()))
            .severity(log.getLevel().name())
            .attributes(log.toLogMap())
            .bodyValue(LogEntry.toPrettyString(log, maxMessageSize))
            .build();
    }

    public static long instantInNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000 + instant.getNano();
    }

}
