package io.kestra.jdbc.runner;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.queue.type", pattern = "mysql|postgres|h2|memory")
@DefaultImplementation
public @interface JdbcRunnerEnabled {
}