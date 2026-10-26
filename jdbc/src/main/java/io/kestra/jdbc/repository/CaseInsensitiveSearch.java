package io.kestra.jdbc.repository;

import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.CustomField;
import org.jooq.impl.SQLDataType;

/**
 * Keeps user-facing text search case-insensitive on MySQL, whose identifier columns use a case-sensitive
 * collation so that two identifiers differing only by case stay distinct. Other dialects are rendered unchanged.
 */
public final class CaseInsensitiveSearch {
    public static final String MYSQL_COLLATION = "utf8mb4_unicode_ci";

    private CaseInsensitiveSearch() {
    }

    public static Field<Object> of(Field<Object> column) {
        return CustomField.of("case_insensitive", SQLDataType.OTHER, ctx ->
        {
            ctx.visit(column);
            if (ctx.family() == SQLDialect.MYSQL) {
                ctx.sql(" COLLATE ").sql(MYSQL_COLLATION);
            }
        });
    }
}
