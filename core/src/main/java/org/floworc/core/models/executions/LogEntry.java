package org.floworc.core.models.executions;

import lombok.Value;
import org.slf4j.event.Level;

import java.time.Instant;

@Value
public class LogEntry {
    Instant timestamp;

    Level level;

    String thread;

    String message;
}
