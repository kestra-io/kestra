package io.kestra.core.models.tasks;

import io.kestra.core.models.flows.State;
import lombok.Value;

import java.time.Duration;
import java.time.Instant;

@Value
public class TaskResult {
    State state;

    Instant start;

    Duration duration;
}
