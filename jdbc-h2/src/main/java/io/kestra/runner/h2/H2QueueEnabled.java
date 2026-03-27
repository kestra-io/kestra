package io.kestra.runner.h2;

import java.lang.annotation.*;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.queue.type", pattern = "h2|memory")
@DefaultImplementation
public @interface H2QueueEnabled {
}