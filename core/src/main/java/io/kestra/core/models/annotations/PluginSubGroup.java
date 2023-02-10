package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.PACKAGE})
public @interface PluginSubGroup {
    String title() default "";
    String description() default "";
}
