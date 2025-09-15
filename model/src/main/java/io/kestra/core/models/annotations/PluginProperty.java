package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface PluginProperty {
    String CORE_GROUP = "core";

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

    /**
     * @return whether the property is an internal storage URI
     */
    boolean internalStorageURI() default false;

    /**
     * @return the group of the property (for the NoCode editor properties grouping).
     */
    String group()  default "";

    /**
     * @return true if this property needs to be hidden from the documentation.
     */
    boolean hidden() default false;
}
