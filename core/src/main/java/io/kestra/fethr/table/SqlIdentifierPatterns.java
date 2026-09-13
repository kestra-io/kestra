package io.kestra.fethr.table;

import java.util.regex.Pattern;

/**
 * The identifier shapes a user-defined table accepts.
 *
 * <p>
 * A column and a table name are deliberately different: a column allows mixed case and a leading
 * underscore, a table is lowercase only. Both are anchored and length-capped, because they are
 * interpolated into DDL -- validation here is what makes that safe.
 */
public interface SqlIdentifierPatterns {

    Pattern COLUMN_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]{0,62}$");

    Pattern TABLE_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]{0,63}$");

    /** The implicit primary-key column every user table gets. Reserved: no user column may take it. */
    String PRIMARY_KEY = "_id";
}
