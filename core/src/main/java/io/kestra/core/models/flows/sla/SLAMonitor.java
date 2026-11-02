package io.kestra.core.models.flows.sla;

import java.time.Duration;
import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class SLAMonitor implements HasUID {
    String executionId;
    String slaId;
    Instant deadline;
    Duration duration;

    @Override
    public String uid() {
        return IdUtils.fromParts(executionId, slaId);
    }
}
