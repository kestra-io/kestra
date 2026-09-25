package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks an API as experimental. A {@code @Beta} type or method may change shape or be removed in a
 * later release without going through the usual deprecation cycle.
 */
@Documented
@Retention(RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE })
public @interface Beta {
}
