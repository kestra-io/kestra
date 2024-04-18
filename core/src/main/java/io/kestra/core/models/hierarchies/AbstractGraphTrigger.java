package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.AbstractTriggerForExecution;
import io.kestra.core.models.triggers.AbstractTriggerInterface;
import io.kestra.core.models.triggers.Trigger;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTrigger extends AbstractGraph {
    @Setter
    private AbstractTriggerInterface triggerDeclaration;
    private final Trigger trigger;

    public AbstractGraphTrigger(AbstractTrigger triggerDeclaration, Trigger trigger) {
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
