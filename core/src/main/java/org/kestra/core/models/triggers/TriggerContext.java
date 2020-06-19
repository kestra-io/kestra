package org.kestra.core.models.triggers;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.flows.Flow;

import java.time.ZonedDateTime;

@Value
@Builder
public class TriggerContext {
    private Flow flow;
    private ZonedDateTime date;
}
