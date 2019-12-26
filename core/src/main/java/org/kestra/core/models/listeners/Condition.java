package org.kestra.core.models.listeners;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import javax.validation.constraints.NotNull;
import java.util.function.BiPredicate;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class Condition implements BiPredicate<Flow, Execution> {
    @NotNull
    protected String type;
}
