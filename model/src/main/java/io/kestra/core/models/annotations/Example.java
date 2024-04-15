package io.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Inherited
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Repeatable(Examples.class)
public @interface Example {
    /**
     * @return The short description of current element
     */
    String title() default "";

    /**
     * @return The code of current element
     */
    String[] code() default "";

    /**
     * @return The language of current element
     */
    String lang() default "yaml";

    /**
     * @return If the example is full (in this case, don't auto add type and id property
     */
    boolean full() default false;
}
