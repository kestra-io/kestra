package io.kestra.fethr.table.validation;

import java.util.Set;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext;
import jakarta.inject.Singleton;

/**
 * Validates a table name against the configured strategy and the reserved list.
 *
 * <p>
 * A null name is left alone: on create {@code @NotBlank} covers it, and on rename an absent name
 * means "keep the current one".
 *
 * <p>
 * <strong>Deliberately not {@code @Introspected}</strong>, unlike every other validator here.
 * Micronaut resolves a constraint validator by looking for an introspection first and only falls
 * back to the bean context, and the introspection route can only instantiate a no-arg constructor.
 * With an introspection present this class could never be built, so reading a create or rename body
 * failed before any field was examined and every table create returned a 422. Without it the bean
 * context supplies the singleton with {@link TablesConfig} injected. Adding a no-arg constructor
 * would be the wrong fix: the introspection would win again and the strategy would silently fall
 * back to STRICT, quietly disabling RELAXED.
 */
@Singleton
public class TableNameValidator implements ConstraintValidator<TableName, String> {

    /** PostgreSQL reserved words, plus Kestra's own table and envelope names. */
    private static final Set<String> RESERVED_NAMES = Set.of(
        "_meta", "audit_log", "kv", "namespace", "namespaces", "pg_class", "pg_index", "public", "role",
        "roles", "secrets", "system", "table_columns", "table_indexes", "table_rows", "tables", "tenant",
        "tenant_id", "tenants", "user", "users", "version"
    );

    private final TablesConfig tablesConfig;

    public TableNameValidator(TablesConfig tablesConfig) {
        this.tablesConfig = tablesConfig;
    }

    @Override
    public boolean isValid(
        @Nullable String value,
        @NonNull AnnotationValue<TableName> annotationMetadata,
        @NonNull ConstraintValidatorContext context) {
        return value == null || (tablesConfig.getNameValidation().matches(value) && !RESERVED_NAMES.contains(value));
    }
}
