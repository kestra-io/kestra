package org.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Stats {
    private double avg;
}
