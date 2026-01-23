package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.Regex;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.function.Function;

@SuperBuilder
@Getter
@NoArgsConstructor
public class MultiselectInput extends Input<List<String>> implements ItemTypeInterface, RenderableInput {
    @Schema(
        title = "Deprecated, please use `values` instead."
    )
//    @NotNull
    @Deprecated
    List<@Regex String> options;

    @Schema(
        title = "List of values available."
    )
    // FIXME: REMOVE `options` in 0.20 and bring back the NotNull
    // @NotNull
    List<@Regex String> values;

    @Schema(
        title = "Expression to be used for dynamically generating the list of values"
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
            return Property.ofValue(List.of(values.getFirst()));
        }

        return baseDefaults;
    }

    @Override
    public void validate(List<String> inputs) throws ConstraintViolationException {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (values != null && options != null) {
            violations.add( ManualConstraintViolation.of(
                "you can't define both `values` and `options`",
                this,
                MultiselectInput.class,
                getId(),
                ""
            ));
        }

        if (!this.getAllowCustomValue()) {
            for (String input : inputs) {
                List<@Regex String> finalValues = this.values != null ? this.values : this.options;
                if (!finalValues.contains(input)) {
                    violations.add(ManualConstraintViolation.of(
                        "value `" + input + "` doesn't match the values `" + finalValues + "`",
                        this,
                        MultiselectInput.class,
                        getId(),
                        input
                    ));
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

    private List<String> renderExpressionValues(final Function<String, Object> renderer) {
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
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }

        String type = Optional.ofNullable(result).map(Object::getClass).map(Class::getSimpleName).orElse("<null>");
        throw ManualConstraintViolation.toConstraintViolationException(
            "Invalid expression result. Expected a list of strings",
            this,
            MultiselectInput.class,
            getId(),
            this
        );
    }
}
