package io.kestra.fethr.table;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plugin property whose value names a user-defined table.
 *
 * <p>
 * A UI hint: the no-code form offers the tenant's tables, while still letting a value be typed that
 * is not on the list -- a Pebble expression, or a table created after the panel loaded. The task
 * still validates the name it is actually given.
 *
 * <p>
 * Carries no members, unlike {@link ColumnReference}: a column catalogue depends on which table a
 * sibling names, whereas the table catalogue is just the tenant's own list.
 */
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableReference {
}
