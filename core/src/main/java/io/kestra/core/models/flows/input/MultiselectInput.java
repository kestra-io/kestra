package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.flows.Type;
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
import java.util.Optional;
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
    Boolean allowInput = false;

    @Override
    public void validate(List<String> inputs) throws ConstraintViolationException {
        if (values != null && options != null) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "you can't define both `values` and `options`",
                this,
                MultiselectInput.class,
                getId(),
                ""
            );
        }

        if (!this.getAllowInput()) {
            for (String input : inputs) {
                List<@Regex String> finalValues = this.values != null ? this.values : this.options;
                if (!finalValues.contains(input)) {
                    throw ManualConstraintViolation.toConstraintViolationException(
                        "it must match the values `" + finalValues + "`",
                        this,
                        MultiselectInput.class,
                        getId(),
                        input
                    );
                }
            }
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
                .allowInput(getAllowInput())
                .required(getRequired())
                .defaults(getDefaults())
                .description(getDescription())
                .dependsOn(getDependsOn())
                .itemType(getItemType())
                .displayName(getDisplayName())
                .build();
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private List<String> renderExpressionValues(final Function<String, Object> renderer) {
        Object result;
        try {
            result = renderer.apply(expression);
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
            return (List<String>) list;
        }
        String type = Optional.ofNullable(result).map(Object::getClass).map(Class::getSimpleName).orElse("<null>");
        throw ManualConstraintViolation.toConstraintViolationException(
            "Invalid expression result. Expected a list of strings, but received " + type,
            this,
            MultiselectInput.class,
            getId(),
            this
        );
    }
}
