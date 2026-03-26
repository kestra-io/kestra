package io.kestra.core.repositories;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a repository as a Micronaut bean.
 * Convenient annotation for `@Singleton` and `@Requires(property = "kestra.server-type", notEquals = "WORKER")`.
 */
@Documented
@Singleton
@Requires(property = "kestra.server-type", notEquals = "WORKER")
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD })
public @interface RepositoryBean {
}
