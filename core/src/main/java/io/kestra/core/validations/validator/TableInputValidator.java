package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.TableInput;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.validations.TableInputValidation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

@Singleton
public class TableInputValidator implements ConstraintValidator<TableInputValidation, TableInput> {
    public static final Set<Type> ALLOWED_COLUMN_TYPES = EnumSet.of(
        Type.STRING, Type.INT, Type.FLOAT, Type.BOOL, Type.DATE, Type.DATETIME, Type.TIME, Type.DURATION,
        Type.EMAIL, Type.URI, Type.SELECT, Type.MULTISELECT
    );

    public static final Set<String> FORBIDDEN_COLUMN_PROPERTIES = Set.of("defaults", "dependsOn");

    @Override
    public boolean isValid(@Nullable TableInput value, @NonNull AnnotationValue<TableInputValidation> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        List<String> violations = fieldViolations("columns", value.getColumns());
        Set<String> columnIds = new HashSet<>(ListUtils.emptyOnNull(value.getColumns()).stream().map(Input::getId).toList());
        ListUtils.emptyOnNull(value.getFilters()).stream()
            .filter(filter -> filter.column() != null && !columnIds.contains(filter.column()))
            .forEach(filter -> violations.add("filter on `%s` is not about a declared column".formatted(filter.column())));
        ListUtils.emptyOnNull(value.getFilters()).stream()
            .filter(filter -> filter.value() instanceof Collection<?> values && values.isEmpty())
            .forEach(filter -> violations.add("filter on `%s` has an empty list to compare the cells to".formatted(filter.column())));
        if (value.getMinRows() != null && value.getMaxRows() != null && value.getMinRows() > value.getMaxRows()) {
            violations.add("`minRows` cannot be greater than `maxRows`");
        }
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        violations.forEach(violation -> context.buildConstraintViolationWithTemplate(violation).addConstraintViolation());
        return false;
    }

    /**
     * The rules every typed field of a record must follow: a regular scalar input, resolved against one key of the
     * record. {@code fieldsName} is the property holding the fields, used in the messages.
     */
    static List<String> fieldViolations(String fieldsName, List<Input<?>> fields) {
        List<String> violations = new ArrayList<>();
        if (ListUtils.isEmpty(fields)) {
            violations.add("`" + fieldsName + "` must declare at least one field");
            return violations;
        }

        Set<String> ids = new HashSet<>();
        for (Input<?> field : fields) {
            String id = field.getId();
            if (id == null) {
                continue;
            }
            if (!ids.add(id)) {
                violations.add("`" + fieldsName + "` declares the id `" + id + "` more than once");
            }
            if (id.contains(".")) {
                violations.add("field `" + id + "` id cannot contain a dot");
            }
            if (field.getType() != null && !ALLOWED_COLUMN_TYPES.contains(field.getType())) {
                violations.add("field `" + id + "` cannot be of type " + field.getType());
            }
            if (field.getDependsOn() != null) {
                violations.add("field `" + id + "` cannot declare `dependsOn`");
            }
            if (field.getDefaults() != null) {
                violations.add("field `" + id + "` cannot declare `defaults`");
            }
        }
        return violations;
    }
}
