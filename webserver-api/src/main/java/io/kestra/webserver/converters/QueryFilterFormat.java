package io.kestra.webserver.converters;

import java.lang.annotation.*;

import io.micronaut.core.bind.annotation.Bindable;

@Bindable
@Target({ ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface QueryFilterFormat {
}