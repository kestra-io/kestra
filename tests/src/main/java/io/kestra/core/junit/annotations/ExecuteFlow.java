package io.kestra.core.junit.annotations;

import io.kestra.core.junit.extensions.FlowExecutorExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

import org.junit.jupiter.api.extension.ExtendWith;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FlowExecutorExtension.class)
public @interface ExecuteFlow {
    String value();

    String timeout() default "PT60S";
}