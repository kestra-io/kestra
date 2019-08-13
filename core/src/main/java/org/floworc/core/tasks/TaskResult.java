package org.floworc.core.tasks;

import lombok.Data;
import org.floworc.core.flows.State;

import java.time.Duration;
import java.time.Instant;

@Data
public class TaskResult {
    private State state;

    private Instant start;

    private Duration duration;
}
