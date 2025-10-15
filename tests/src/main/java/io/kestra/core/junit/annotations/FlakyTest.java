package io.kestra.core.junit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * used to document that a test is flaky and needs to be reworked
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("flaky")
public @interface FlakyTest {

    /**
     * Use to explain why the test is flaky
     */
    String description() default "";
}