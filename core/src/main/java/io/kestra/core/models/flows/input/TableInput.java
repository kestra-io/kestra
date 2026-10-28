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
@EeOnly
@TableInputValidation
public class TableInput extends Input<List<?>> {
    @Schema(
        title = "The typed columns of the table.",
        description = "Each column is a regular input declaration (e.g. `INT` with `min`/`max`, `STRING` with a `validator`) " +
            "applied to the corresponding key of every row. The value is a list of objects, referenced as " +
            "`{{ inputs.myTable[0].myColumn }}`. Columns cannot be of type `TABLE`, `ARRAY`, `FORM`, `FILE`, `SECRET`, " +
            "`JSON`, `ION` or `YAML`, and cannot declare `defaults`, `prefill`, `dependsOn` or an `expression`. " +
            "This input type is only available in the Enterprise Edition."
    )
    @NotNull
    @Valid
    private List<Input<?>> columns;

    @Schema(title = "Minimum number of rows.")
    @Min(0)
    private Integer minRows;

    @Schema(title = "Maximum number of rows.")
    @Min(1)
    private Integer maxRows;

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        if (minRows != null && input.size() < minRows) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at least `" + minRows + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }

        if (maxRows != null && input.size() > maxRows) {
            throw ManualConstraintViolation.toConstraintViolationException(
                "it must have at most `" + maxRows + "` rows",
                this,
                TableInput.class,
                getId(),
                input
            );
        }
    }
}
