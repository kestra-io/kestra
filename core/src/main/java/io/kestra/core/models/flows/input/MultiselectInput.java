package io.kestra.core.models.flows.input;

import io.kestra.core.models.flows.Input;
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

@SuperBuilder
@Getter
@NoArgsConstructor
public class MultiselectInput extends Input<List<String>> implements ItemTypeInterface {
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
}
