package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.model.TriggerState;

public class GraphTrigger extends AbstractGraphTrigger {
    @JsonCreator
    public GraphTrigger(
        AbstractTrigger triggerDeclaration,
        TriggerState trigger) {
        super(triggerDeclaration, trigger);
    }
}
