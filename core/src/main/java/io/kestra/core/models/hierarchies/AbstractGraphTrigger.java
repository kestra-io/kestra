package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTrigger extends AbstractGraph {
    private final AbstractTrigger triggerDeclaration;
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
}
