package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.scheduler.model.TriggerState;

public class GraphTrigger extends AbstractGraphTrigger {
    public GraphTrigger(AbstractTrigger triggerDeclaration, TriggerState trigger) {
        super(triggerDeclaration, trigger);
    }
}
