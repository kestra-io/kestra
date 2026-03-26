package io.kestra.core.models.tasks;

import java.time.Duration;
import java.time.Instant;

import io.kestra.core.models.flows.State;

public record TaskResult(
    State state,

    Instant start,

    Duration duration) {
}
