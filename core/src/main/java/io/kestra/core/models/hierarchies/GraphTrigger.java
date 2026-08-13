package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.model.TriggerState;

public class GraphTrigger extends AbstractGraphTrigger {
    // Explicit creator, for the same reason as GraphTask's: this hierarchy has no default constructor and
    // @Introspected is no longer a fallback creator source. Reached whenever a FlowGraph carries a trigger node.
    @JsonCreator
    public GraphTrigger(
        AbstractTrigger triggerDeclaration,
        TriggerState trigger) {
        super(triggerDeclaration, trigger);
    }
}
