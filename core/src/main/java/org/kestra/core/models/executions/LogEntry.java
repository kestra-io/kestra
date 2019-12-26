package org.kestra.core.models.executions;

import lombok.Builder;
import lombok.Value;
import org.slf4j.event.Level;

import java.time.Instant;

@Value
@Builder
public class LogEntry {
    Instant timestamp;

    Level level;

    String thread;

    String message;
}
