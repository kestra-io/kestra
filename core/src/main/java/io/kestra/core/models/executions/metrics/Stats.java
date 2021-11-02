package io.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Stats {
    double avg;
}
