package io.kestra.fethr.table.validation;

import java.util.regex.Pattern;

import io.kestra.fethr.table.SqlIdentifierPatterns;

/**
 * The shape a user-defined table name must match.
 *
 * <p>
 * {@code STRICT} is the platform rule: a name starts with a lowercase letter. {@code RELAXED} also
 * allows a leading digit, which exists to keep migrated Corepoint code sets under their own names
 * -- several of them start with a digit -- rather than prefixing every one.
 *
 * <p>
 * Neither affects the physical name, which is always {@code tenantId_namespace_name} and so starts
 * with a letter regardless.
 */
public enum TableNameValidationStrategy {
    STRICT(SqlIdentifierPatterns.TABLE_IDENTIFIER),
    RELAXED(Pattern.compile("^[a-z0-9][a-z0-9_]{0,63}$"));

    private final Pattern pattern;

    TableNameValidationStrategy(Pattern pattern) {
        this.pattern = pattern;
    }

    public boolean matches(String name) {
        return pattern.matcher(name).matches();
    }
}
