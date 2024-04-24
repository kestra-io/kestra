package io.kestra.core.models.triggers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
public class AbstractTriggerForExecution implements TriggerInterface {

    protected String id;

    protected String type;

    public static AbstractTriggerForExecution of(AbstractTrigger abstractTrigger) {
        return AbstractTriggerForExecution.builder()
            .id(abstractTrigger.getId())
            .type(abstractTrigger.getType())
            .build();
    }
}
