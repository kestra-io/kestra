package io.kestra.queue;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Bean;
import jakarta.inject.Singleton;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Defines a queue as a Micronaut bean.
 * Convenient annotation for `@Singleton` and `@Bean(preDestroy = "close")`.
 */
@Documented
@Singleton
@Bean(preDestroy = "close")
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.FIELD })
public @interface QueueBean {
}
