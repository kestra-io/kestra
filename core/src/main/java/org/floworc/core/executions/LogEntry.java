package org.floworc.core.executions;

import lombok.Data;
import org.slf4j.event.Level;

import java.time.Instant;

@Data
public class LogEntry {
    Instant timestamp;

    Level level;

    String thread;

    String message;
}
