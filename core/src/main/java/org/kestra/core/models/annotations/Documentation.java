package org.kestra.core.models.annotations;

import java.lang.annotation.*;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface Documentation {
    /**
     * @return The short description of current element
     */
    String description() default "";

    /**
     * @return The body of current element
     */
    String[] body() default "";
}
