package io.kestra.fethr.table.validation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.kestra.plugin.core.tables.Rows;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/**
 * Enforces which of a row task's fields are mandatory for its chosen action.
 *
 * <p>
 * {@code action} and {@code tableName} apply to every action and so are {@code @NotNull} on the
 * fields themselves. Everything else applies to a subset, which can only be expressed here, at the
 * class level, once the action is known -- the {@code @AppliesWhen} hints say the same thing to the
 * editor, but a hint is not enforcement.
 */
@Singleton
@Introspected
public class TableRowsValidator implements ConstraintValidator<TableRowsValidation, Rows> {

    @Override
    public boolean isValid(
        @Nullable Rows rows,
        @NonNull AnnotationValue<TableRowsValidation> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        if (rows == null || rows.getAction() == null) {
            return true;
        }

        List<String> missing = missingFieldsFor(rows);
        if (missing.isEmpty()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
            "`" + String.join("`, `", missing) + "` "
                + (missing.size() == 1 ? "is" : "are")
                + " required when `action` is " + rows.getAction()
        ).addConstraintViolation();

        return false;
    }

    /**
     * The action-specific fields that are absent.
     *
     * <p>
     * Presence only, never emptiness: a filter that renders to an empty map is a run-time risk the
     * task itself guards, because only the rendered value proves it.
     */
    private List<String> missingFieldsFor(Rows rows) {
        return switch (rows.getAction()) {
            case INSERT -> rows.getData() == null ? List.of("data") : List.of();
            case UPDATE -> Stream.of(
                rows.getFilter() == null ? "filter" : null,
                rows.getPatch() == null ? "patch" : null
            )
                .filter(Objects::nonNull)
                .toList();
            case UPSERT -> Stream.of(
                rows.getFilter() == null ? "filter" : null,
                rows.getData() == null ? "data" : null
            )
                .filter(Objects::nonNull)
                .toList();
            case QUERY -> List.of();
            case FIND -> rows.getFilter() == null ? List.of("filter") : List.of();
        };
    }
}
