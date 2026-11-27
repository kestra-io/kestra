package io.kestra.webserver.services.ai;

import java.lang.annotation.*;

import io.micronaut.context.annotation.Requires;

/**
 * Gates an AI Copilot bean on {@code kestra.ai.enabled} (enabled by default).
 * <p>
 * {@code @Requires} is not inherited, so every bean that must disappear when AI is disabled — including
 * subclasses that {@code @Replaces} an OSS bean — has to carry this annotation itself.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.ai.enabled", value = "true", defaultValue = "true")
public @interface RequiresAiEnabled {
}
