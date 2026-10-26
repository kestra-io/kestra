package io.kestra.core.models.flows.input;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
        List<?> values = value instanceof Collection<?> collection ? collection.stream().toList() : Collections.singletonList(value);
        return switch (operation) {
            case EQUALS, IN -> values.stream().anyMatch(candidate -> same(cell, candidate));
            case NOT_EQUALS, NOT_IN -> values.stream().noneMatch(candidate -> same(cell, candidate));
            case CONTAINS -> cell instanceof Collection<?> items
                ? items.stream().anyMatch(item -> values.stream().anyMatch(candidate -> same(item, candidate)))
                : cell != null && values.stream().map(String::valueOf).anyMatch(String.valueOf(cell)::contains);
        };
    }

    private static boolean same(Object cell, Object candidate) {
        if (cell instanceof Number || candidate instanceof Number) {
            BigDecimal a = number(cell);
            BigDecimal b = number(candidate);
            if (a != null && b != null) {
                return a.compareTo(b) == 0;
            }
        }
        return cell != null && String.valueOf(cell).equals(String.valueOf(candidate));
    }

    private static BigDecimal number(Object value) {
        try {
            return value == null ? null : new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
