package org.floworc.core.models.tasks;

import lombok.Value;

import java.time.Duration;

@Value
public class Retry {
    private int limit;

    private RetryIntervalType type;

    private Duration interval;
}
