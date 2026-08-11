package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.*;
import io.kestra.core.scheduler.model.TriggerState;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
public abstract class AbstractGraphTrigger extends AbstractGraph {
    @Setter
    private TriggerInterface triggerDeclaration;
    private final TriggerState trigger;

    public AbstractGraphTrigger(AbstractTrigger triggerDeclaration, TriggerState trigger) {
        super();

        this.triggerDeclaration = triggerDeclaration;
        this.trigger = trigger;
    }

    @Override
    public String getUid() {
        if (this.uid == null && this.triggerDeclaration != null) {
            return this.triggerDeclaration.getId();
        }

        return this.uid;
    }

    @Override
    public AbstractGraph forExecution() {
        this.setTriggerDeclaration(AbstractTriggerForExecution.of((AbstractTrigger) this.getTriggerDeclaration()));

        return this;
    }
}
