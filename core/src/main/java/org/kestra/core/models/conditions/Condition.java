package org.kestra.core.models.conditions;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

import java.util.function.BiPredicate;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "type", visible = true, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public abstract class Condition implements BiPredicate<Flow, Execution> {
    @NotNull
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;
}
