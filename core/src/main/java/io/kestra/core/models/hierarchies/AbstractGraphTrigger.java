package io.kestra.core.models.hierarchies;

import io.kestra.core.models.triggers.AbstractTrigger;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@Introspected
public abstract class AbstractGraphTrigger extends AbstractGraph {
    private final AbstractTrigger trigger;

    public AbstractGraphTrigger(AbstractTrigger trigger) {
        super();

        this.trigger = trigger;
    }

    @Override
    public String getUid() {
        if (this.trigger != null) {
            return this.trigger.getId() + "_trigger";
        }

        return this.uid;
    }
}
