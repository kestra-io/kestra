package io.kestra.runner.postgres;

import java.lang.annotation.*;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.queue.type", value = "postgres")
@DefaultImplementation
public @interface PostgresQueueEnabled {
}