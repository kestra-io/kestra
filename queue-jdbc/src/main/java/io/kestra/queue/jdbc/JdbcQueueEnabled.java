package io.kestra.queue.jdbc;

import java.lang.annotation.*;

import io.micronaut.context.annotation.Requires;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.queue.type", pattern = "memory|h2|mysql|postgres")
public @interface JdbcQueueEnabled {
}