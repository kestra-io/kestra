package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@SuperBuilder
@Getter
@NoArgsConstructor
public class SelectInput extends Input<String> implements RenderableInput {

    @Schema(
        title = "List of values."
    )
    List<@Regex String> values;

    @Schema(
        title = "Expression to be used for dynamically generating the list of values"
    )
    String expression;

    @Schema(
        title = "If the user can provide a custom value."
    )
    @NotNull
    @Builder.Default
    Boolean allowCustomValue = false;

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (!values.contains(input) & this.getRequired()) {
            if (this.getAllowCustomValue()) {
                return;
            }
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must match the values `" + values + "`",
                this,
                SelectInput.class,
                getId(),
                input
            );
        }
    }

    /** {@inheritDoc} **/
    @Override
    public Input<?> render(final Function<String, Object> renderer) {
        if (expression != null) {
            return SelectInput
                .builder()
                .values(renderExpressionValues(renderer))
                .id(getId())
                .type(getType())
                .allowCustomValue(getAllowCustomValue())
                .required(getRequired())
                .defaults(getDefaults())
                .description(getDescription())
                .dependsOn(getDependsOn())
                .displayName(getDisplayName())
                .build();
        }
        return this;
    }

    private List<String> renderExpressionValues(final Function<String, Object> renderer) {
        Object result;
        try {
            result = renderer.apply(expression);
        } catch (Exception e) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "Cannot render 'expression'. Cause: " + e.getMessage(),
                this,
                SelectInput.class,
                getId(),
                this
            );
        }

        if (result instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }

        String type = Optional.ofNullable(result).map(Object::getClass).map(Class::getSimpleName).orElse("<null>");
        throw ManualConstraintViolation.toConstraintViolationException(
            "Invalid expression result. Expected a list of strings, but received " + type,
            this,
            SelectInput.class,
            getId(),
            this
        );
    }
}
