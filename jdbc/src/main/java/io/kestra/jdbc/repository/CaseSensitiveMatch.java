package io.kestra.jdbc.repository;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.CustomCondition;
import org.jooq.impl.DSL;

/**
 * Exact-case equality and prefix conditions for columns that MySQL still compares case-insensitively.
 * The plain predicate is kept so its index narrows the rows, and a binary comparison removes the
 * case variants. Other dialects are rendered unchanged.
 */
public final class CaseSensitiveMatch {
    private CaseSensitiveMatch() {
    }

    public static Condition eq(Field<String> column, String value) {
        return withBinaryCheck(column.eq(value), DSL.condition("cast({0} as binary) = cast({1} as binary)", column, DSL.val(value)));
    }

    public static Condition startsWith(Field<String> column, String prefix) {
        Condition binary = DSL.condition(
            "cast(left({0}, {1}) as binary) = cast({2} as binary)",
            column, DSL.inline(prefix.codePointCount(0, prefix.length())), DSL.val(prefix)
        );
        return withBinaryCheck(column.startsWith(prefix), binary);
    }

    private static Condition withBinaryCheck(Condition condition, Condition binaryCheck) {
        return CustomCondition.of(ctx ->
        {
            if (ctx.family() == SQLDialect.MYSQL) {
                ctx.sql("(").visit(condition).sql(" and ").visit(binaryCheck).sql(")");
            } else {
                ctx.visit(condition);
            }
        });
    }
}
