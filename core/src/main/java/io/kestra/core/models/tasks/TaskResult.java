package io.kestra.core.models.tasks;

import io.kestra.core.models.flows.State;

import java.time.Duration;
import java.time.Instant;

public record TaskResult(
    State state,

    Instant start,

    Duration duration) {
}
