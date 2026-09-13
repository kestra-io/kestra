package io.kestra.fethr.table;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a plugin property whose value -- or, for a map-typed property, whose keys -- name columns of
 * the table a sibling property names.
 *
 * <p>
 * A UI hint, like {@link TableReference}: the form offers that table's columns without preventing a
 * value being typed that is not among them.
 */
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ColumnReference {

    /** The sibling property holding the table name whose columns this one refers to. */
    String property();
}
