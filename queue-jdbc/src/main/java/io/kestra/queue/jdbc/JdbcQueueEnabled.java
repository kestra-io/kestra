package io.kestra.queue.jdbc;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.queue.type", value = "jdbc")
public @interface JdbcQueueEnabled {
}