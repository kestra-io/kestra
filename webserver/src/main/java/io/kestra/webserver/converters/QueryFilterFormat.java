package io.kestra.webserver.converters;


import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

@Bindable
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface QueryFilterFormat {
}