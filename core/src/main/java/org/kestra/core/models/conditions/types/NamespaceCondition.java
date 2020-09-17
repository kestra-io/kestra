package org.kestra.core.models.conditions.types;

import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Introspected
public class NamespaceCondition extends Condition {
    @NotNull
    public String namespace;

    @Valid
    @Builder.Default
    public boolean prefix = false;

    @Override
    public boolean test(Flow flow, Execution execution) {
        if (!prefix && execution.getNamespace().equals(this.namespace)) {
            return  true;
        }

        if (prefix && execution.getNamespace().startsWith(this.namespace)) {
            return  true;
        }

        return false;
    }
}
