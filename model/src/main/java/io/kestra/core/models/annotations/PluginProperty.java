package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface PluginProperty {
    /**
     * @return whether the property is renderer
     */
    boolean dynamic() default false;

    /**
     * @return the Class for a map
     */
    Class<?> additionalProperties() default Object.class;

    /**
     * @return whether the property is in beta
     */
    boolean beta() default false;
}
