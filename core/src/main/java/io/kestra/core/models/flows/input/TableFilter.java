package io.kestra.core.models.flows.input;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(title = "A condition a row of a `TABLE` input must match to be shown and passed to the execution.")
public record TableFilter(
    @Schema(title = "The id of the column the condition is about.")
    @NotBlank String column,

    @Schema(title = "How the cell is compared to `value`.")
    @NotNull Operation operation,

    @Schema(
        title = "The value the cell is compared to.",
        description = "Can be an expression, e.g. `{{ inputs.component_types }}`, rendered with the inputs listed in `dependsOn`. " +
            "`IN` and `NOT_IN` take a list."
    )
    @NotNull Object value) {
    public enum Operation {
        EQUALS,
        NOT_EQUALS,
        IN,
        NOT_IN,
        CONTAINS
    }

    public boolean matches(Map<?, ?> row) {
        Object cell = row.get(column);
        String text = cell == null ? null : String.valueOf(cell);
        List<String> values = value instanceof Collection<?> collection
            ? collection.stream().map(String::valueOf).toList()
            : List.of(String.valueOf(value));
        return switch (operation) {
            case EQUALS -> Objects.equals(text, String.valueOf(value));
            case NOT_EQUALS -> !Objects.equals(text, String.valueOf(value));
            case IN -> values.contains(text);
            case NOT_IN -> !values.contains(text);
            case CONTAINS -> cell instanceof Collection<?> items
                ? items.stream().map(String::valueOf).anyMatch(values::contains)
                : text != null && values.stream().anyMatch(text::contains);
        };
    }
}
