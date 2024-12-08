package io.kestra.core.models.triggers;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class AbstractTriggerForExecution implements TriggerInterface {

    protected String id;

    protected String type;

    protected String version;

    public static AbstractTriggerForExecution of(AbstractTrigger abstractTrigger) {
        return AbstractTriggerForExecution.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .build();
    }
}
