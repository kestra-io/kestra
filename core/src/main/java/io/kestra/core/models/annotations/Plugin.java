package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Plugin {
    Example[] examples();

    Metric[] metrics() default {};

    /**
     * @return whether the plugin is in beta
     */
    boolean beta() default false;
}
