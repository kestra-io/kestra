package org.floworc.runner.memory;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "floworc.queue.type", value = "memory")
@DefaultImplementation
public @interface MemoryQueueEnabled {
}