package io.kestra.core.runners.pebble;

import java.lang.annotation.*;

/**
 * Marks a Pebble function or filter as deprecated, carrying the name of the recommended replacement.
 * Apply alongside Java's {@link Deprecated} annotation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DeprecatedPebble {
    /** Name of the Pebble function or filter that should be used instead. Empty if no replacement exists. */
    String replaceWith() default "";
}
