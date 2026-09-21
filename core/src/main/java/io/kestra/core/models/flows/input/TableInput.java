package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.function.Function;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.RenderableInput;
import io.kestra.core.models.validations.ManualConstraintViolation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
@NoArgsConstructor
public class TableInput extends Input<List<?>> implements RenderableInput {
    @Schema(
        title = "The columns of a table row.",
        description = "Each column is an input declaration evaluated once per row, so per-column properties such as " +
            "`min`, `max`, `validator` or `values` apply per cell. A column can only be of a scalar or SELECT type: " +
            "`FILE`, `SECRET`, `JSON`, `ION`, `YAML`, `ARRAY`, `FORM` and `TABLE` are rejected."
    )
    @NotNull
    @NotEmpty
    @Valid
    private List<Input<?>> columns;

    @Schema(title = "The bounds on the number of rows the user can enter.")
    private Rows rows;

    @Override
    public void validate(List<?> input) throws ConstraintViolationException {
        if (rows == null) {
            return;
        }

        if (rows.min() != null && input.size() < rows.min()) {
            throw violation("it must have at least " + rows.min() + " row(s)", input);
        }

        if (rows.max() != null && input.size() > rows.max()) {
            throw violation("it must have at most " + rows.max() + " row(s)", input);
        }
    }

    /** Renders each column, so a `SELECT` column's `expression` resolves once per column rather than per cell. */
    @Override
    public Input<?> render(final Function<String, Object> renderer) {
        return TableInput.builder()
            .columns(columns.stream().<Input<?>>map(column -> RenderableInput.mayRenderInput(column, renderer)).toList())
            .rows(rows)
            .id(getId())
            .type(getType())
            .required(getRequired())
            .defaults(getDefaults())
            .description(getDescription())
            .dependsOn(getDependsOn())
            .displayName(getDisplayName())
            .build();
    }

    private ConstraintViolationException violation(String message, List<?> input) {
        return ManualConstraintViolation.toConstraintViolationException(message, this, TableInput.class, getId(), input);
    }

    public record Rows(Integer min, Integer max) {
    }
}
