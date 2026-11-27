package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Names the {@link PluginProperty#group()} values in which at least one property must be set, emitted as {@code $requiredGroups}.
 */
@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface RequiredGroups {
    String[] value();
}
