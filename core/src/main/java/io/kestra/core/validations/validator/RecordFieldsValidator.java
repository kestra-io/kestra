package io.kestra.core.validations.validator;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.models.flows.input.SelectInput;
import io.kestra.core.utils.ListUtils;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;

/**
 * Rules shared by every input made of typed fields: the {@code properties} of an {@code OBJECT} and the
 * {@code columns} of a {@code TABLE}. A field is a regular scalar input resolved against one key of a record.
 */
final class RecordFieldsValidator {
    static final Set<Type> ALLOWED_FIELD_TYPES = EnumSet.of(
        Type.STRING, Type.INT, Type.FLOAT, Type.BOOL, Type.DATE, Type.DATETIME, Type.TIME, Type.DURATION,
        Type.EMAIL, Type.URI, Type.SELECT, Type.MULTISELECT
    );

    private RecordFieldsValidator() {
    }

    static List<String> violations(String fieldsName, List<Input<?>> fields) {
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
            if (field.getType() != null && !ALLOWED_FIELD_TYPES.contains(field.getType())) {
                violations.add("field `" + id + "` cannot be of type " + field.getType());
            }
            if (field.getDependsOn() != null) {
                violations.add("field `" + id + "` cannot declare `dependsOn`");
            }
            if (field.getDefaults() != null) {
                violations.add("field `" + id + "` cannot declare `defaults`");
            }
            if (field.getPrefill() != null) {
                violations.add("field `" + id + "` cannot declare `prefill`");
            }
            if (field instanceof SelectInput select && select.getExpression() != null
                || field instanceof MultiselectInput multiselect && multiselect.getExpression() != null) {
                violations.add("field `" + id + "` cannot declare an `expression`");
            }
        }
        return violations;
    }

    static boolean report(List<String> violations, @NonNull ConstraintValidatorContext context) {
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        violations.forEach(violation -> context.buildConstraintViolationWithTemplate(violation).addConstraintViolation());
        return false;
    }
}
