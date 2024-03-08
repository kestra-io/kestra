package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;


public class GraphTrigger extends AbstractGraphTrigger {
    public GraphTrigger(AbstractTrigger triggerDeclaration, Trigger trigger) {
        super(triggerDeclaration, trigger);
    }
}
