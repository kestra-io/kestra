package io.kestra.core.models.tasks.logs;

import io.kestra.core.models.executions.LogEntry;
import java.time.Instant;

public final class LogRecordMapper {

    private LogRecordMapper(){}

    public static LogRecord mapToLogRecord(LogEntry log) {
        return LogRecord.builder()
            .resource("Kestra")
            .timestampEpochNanos(instantInNanos(log.getTimestamp()))
            .severity(log.getLevel().name())
            .attributes(log.toLogMap())
            .bodyValue(LogEntry.toPrettyString(log))
            .build();
    }

    public static long instantInNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000 + instant.getNano();
    }

}
