package io.kestra.core.models.hierarchies;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.ToString;

@ToString
@Getter
@Introspected
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
public abstract class AbstractGraphTrigger extends AbstractGraph {
    private final AbstractTrigger trigger;

    public AbstractGraphTrigger() {
        super();

        this.trigger = null;
    }

    public AbstractGraphTrigger(AbstractTrigger trigger) {
        super();

        this.trigger = trigger;
    }


    @Override
    public String getUid() {
        if (this.trigger != null) {
            return this.trigger.getId();
        }
        return this.uid;
    }
}
