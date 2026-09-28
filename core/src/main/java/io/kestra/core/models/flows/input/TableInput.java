package io.kestra.core.models.flows.input;

import java.util.List;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.validations.TableInputValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
@TableInputValidation
public class TableInput extends Input<List<?>> {
    @Schema(
        title = "The typed columns of the table.",
        description = "Each column is a regular input declaration (e.g. `INT` with `min`/`max`, `STRING` with a `validator`) " +
            "applied to the corresponding key of every row. The value is a list of objects, referenced as " +
            "`{{ inputs.myTable[0].myColumn }}` or iterated with `ForEach`. Columns follow the same rules as the " +
            "properties of an `OBJECT` input."
    )
    @NotNull
    @Valid
    private List<Input<?>> columns;

    @Schema(
        title = "Constraints on the number of rows."
    )
    @Valid
    private Rows rows;

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        if (rows == null) {
            return;
        }

        if (rows.min() != null && input.size() < rows.min()) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at least `" + rows.min() + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }

        if (rows.max() != null && input.size() > rows.max()) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at most `" + rows.max() + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }
    }

    public record Rows(
        @Schema(title = "Minimum number of rows.") @Min(0) Integer min,
        @Schema(title = "Maximum number of rows.") @Min(1) Integer max
    ) {
    }
}
