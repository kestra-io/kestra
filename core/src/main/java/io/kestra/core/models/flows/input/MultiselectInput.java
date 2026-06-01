package io.kestra.core.models.flows.input;

import java.util.*;
import java.util.function.Function;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.MultiselectInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@MultiselectInputValidation
public class MultiselectInput extends Input<List<String>> implements ItemTypeInterface, RenderableInput {
    @Schema(
        title = "List of values available.",
        description = "Each item is either a plain string (used as both label and value) or an object `{label, value}` to decouple the displayed label from the workflow value."
    )
    @NotNull
    List<@Valid ValueOption> values;

    @Schema(
        title = "Expression to be used for dynamically generating the list of values."
    )
    String expression;

    @Schema(
        title = "Type of the different values available.",
        description = "Cannot be of type `ARRAY` nor 'MULTISELECT'."
    )
    @Builder.Default
    private Type itemType = Type.STRING;

    @Schema(
        title = "If the user can provide customs value."
    )
    @NotNull
    @Builder.Default
    Boolean allowCustomValue = false;

    @Schema(
        title = "Whether the first value of the multi-select should be selected by default."
    )
    @NotNull
    @Builder.Default
    Boolean autoSelectFirst = false;

    @Override
    public Property<List<String>> getDefaults() {
        Property<List<String>> baseDefaults = super.getDefaults();
        if (baseDefaults == null && autoSelectFirst && !Optional.ofNullable(values).map(Collection::isEmpty).orElse(true)) {
            return Property.ofValue(List.of(values.getFirst().value()));
        }

        return baseDefaults;
    }

    @Override
    public void validate(List<String> inputs) throws ConstraintViolationException {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!this.getAllowCustomValue()) {
            Set<String> allowedValues = this.values.stream().map(ValueOption::value).collect(java.util.stream.Collectors.toSet());
            for (String input : inputs) {
                if (!allowedValues.contains(input)) {
                    violations.add(
                        ManualConstraintViolation.of(
                            "value `" + input + "` doesn't match the values `" + this.values.stream().map(ValueOption::value).toList() + "`",
                            this,
                            MultiselectInput.class,
                            getId(),
                            input
                        )
                    );
                }
            }
        }
        if (!violations.isEmpty()) {
            throw ManualConstraintViolation.toConstraintViolationException(violations);
        }
    }

    /** {@inheritDoc} **/
    @Override
    public Input<?> render(final Function<String, Object> renderer) {
        if (expression != null) {
            return MultiselectInput
                .builder()
                .values(renderExpressionValues(renderer))
                .id(getId())
                .type(getType())
                .allowCustomValue(getAllowCustomValue())
                .required(getRequired())
                .defaults(getDefaults())
                .description(getDescription())
                .dependsOn(getDependsOn())
                .itemType(getItemType())
                .displayName(getDisplayName())
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
                MultiselectInput.class,
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
            MultiselectInput.class,
            getId(),
            this
        );
    }
}
