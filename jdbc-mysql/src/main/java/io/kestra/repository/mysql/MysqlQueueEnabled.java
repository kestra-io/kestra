package io.kestra.repository.mysql;

import java.lang.annotation.*;

import io.micronaut.context.annotation.Requires;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.queue.type", pattern = "(?i)mysql")
public @interface MysqlQueueEnabled {
}
