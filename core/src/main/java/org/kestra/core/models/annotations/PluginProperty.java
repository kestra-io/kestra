package org.kestra.core.models.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.FIELD})
public @interface PluginProperty {
    /**
     * @return If the properties is renderer
     */
    boolean dynamic() default false;

    /**
     * @return the Class for a map
     */
    Class<?> additionalProperties() default Object.class;
}
