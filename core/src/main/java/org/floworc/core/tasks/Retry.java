package org.floworc.core.tasks;

import lombok.*;

import java.time.Duration;

@Data
public class Retry {
    private int limit;

    private RetryIntervalType type;

    private Duration interval;
}
