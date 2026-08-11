package io.kestra.core.junit.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import io.kestra.core.junit.extensions.WithFlowExtension;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(WithFlowExtension.class)
public @interface WithFlow {
    String value();

    String tenantId() default "";
}