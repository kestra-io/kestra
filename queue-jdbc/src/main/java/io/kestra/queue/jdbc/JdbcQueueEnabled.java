package io.kestra.queue.jdbc;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.queue.type", pattern = "memory|h2|mysql|postgres")
public @interface JdbcQueueEnabled {
}