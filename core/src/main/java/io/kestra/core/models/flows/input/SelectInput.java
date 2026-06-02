package io.kestra.core.models.flows.input;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ManualConstraintViolation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class SelectInput extends Input<String> implements RenderableInput {

    @Schema(
        title = "List of values.",
        description = "Each item is either a plain string (used as both label and value) or an object `{label, value}` to decouple the displayed label from the workflow value."
    )
    List<@Valid ValueOption> values;

    @Schema(
        title = "Expression to be used for dynamically generating the list of values."
    )
    String expression;

    @Schema(
        title = "If the user can provide a custom value."
    )
    @NotNull
    @Builder.Default
    Boolean allowCustomValue = false;

    @Schema(
        title = "Indicates if the input should be rendered as a radio button group."
    )
    @NotNull
    @Builder.Default
    Boolean isRadio = false;

    @Schema(
        title = "Whether the first value of the select should be selected by default."
    )
    @NotNull
    @Builder.Default
    Boolean autoSelectFirst = false;

    @Override
    public Property<String> getDefaults() {
        Property<String> baseDefaults = super.getDefaults();
        if (baseDefaults == null && autoSelectFirst && !Optional.ofNullable(values).map(Collection::isEmpty).orElse(true)) {
            return Property.ofValue(values.getFirst().value());
        }

        return baseDefaults;
    }

    @Override
    public void validate(String input) throws ConstraintViolationException {
        if (this.getRequired() && values.stream().noneMatch(v -> Objects.equals(v.value(), input))) {
            if (this.getAllowCustomValue()) {
                return;
            }
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must match the values `" + values.stream().map(ValueOption::value).toList() + "`",
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
                .isRadio(getIsRadio())
                .autoSelectFirst(getAutoSelectFirst())
                .build();
        }
        return this;
    }

    private List<ValueOption> renderExpressionValues(final Function<String, Object> renderer) {
        Object result;
        try {
            result = renderer.apply(expression.trim());
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
            return list.stream().filter(Objects::nonNull).map(ValueOption::from).toList();
        }

        throw ManualConstraintViolation.toConstraintViolationException(
            "Invalid expression result. Expected a list of values",
            this,
            SelectInput.class,
            getId(),
            this
        );
    }
}
