package org.floworc.runner.kafka;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "floworc.queue.type", value = "kafka")
public @interface KafkaQueueEnabled {
}