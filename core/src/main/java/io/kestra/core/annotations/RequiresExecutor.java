package io.kestra.core.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Restricts a bean to the Executor, or to a standalone server.
 *
 * <p>Use it on cluster-global scheduled jobs, such as purges, so that a single component does the
 * work instead of every component operating on the same rows. Jobs acting on component-local state
 * are the exception: metrics must run wherever they are scraped, and Scheduler- or Worker-specific
 * housekeeping belongs to those components.
 *
 * <p>The default value keeps such jobs enabled when {@code kestra.server-type} is not set, as in
 * tests and {@code runLocal}.
 *
 * @see io.kestra.core.models.ServerType
 */
@Documented
@Requires(property = "kestra.server-type", pattern = "(EXECUTOR|STANDALONE)", defaultValue = "STANDALONE")
@Retention(RUNTIME)
@Target({ElementType.PACKAGE, ElementType.ANNOTATION_TYPE, ElementType.TYPE})
public @interface RequiresExecutor {
}
