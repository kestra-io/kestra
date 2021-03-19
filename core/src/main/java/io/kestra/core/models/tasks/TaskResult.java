package io.kestra.core.models.tasks;

import lombok.Value;
import io.kestra.core.models.flows.State;

import java.time.Duration;
import java.time.Instant;

@Value
public class TaskResult {
    private State state;

    private Instant start;

    private Duration duration;
}
