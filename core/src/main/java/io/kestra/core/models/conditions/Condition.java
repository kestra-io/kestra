package io.kestra.core.models.conditions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Plugin;
import io.kestra.core.utils.Rethrow;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@io.kestra.core.models.annotations.Plugin
@SuperBuilder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public abstract class Condition implements Plugin, Rethrow.PredicateChecked<ConditionContext, InternalException> {
    @NotNull
    @Pattern(regexp="\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*")
    protected String type;
}
