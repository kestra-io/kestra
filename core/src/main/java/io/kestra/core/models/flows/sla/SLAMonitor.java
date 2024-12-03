package io.kestra.core.models.flows.sla;

import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Builder
@Getter
public class SLAMonitor implements HasUID {
    String executionId;
    String slaId;
    Instant deadline;

    @Override
    public String uid() {
        return IdUtils.fromParts(executionId, slaId);
    }
}
