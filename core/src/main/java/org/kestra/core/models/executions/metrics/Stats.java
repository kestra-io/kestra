package org.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Stats {
    private long count;
    private double min;
    private double max;
    private double sum;
    private double avg;
}
