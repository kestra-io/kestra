package io.kestra.webserver.converters;

import java.lang.annotation.*;

import io.kestra.core.models.QueryFilter;

import io.micronaut.core.bind.annotation.Bindable;

/**
 * Marks a controller parameter as receiving filters in the LHS-bracket query format.
 * The {@link #value()} identifies which {@link QueryFilter.Resource} is being filtered, so the binder can
 * resolve per-Resource depth/width caps from {@code kestra.webserver.query-filter.resources.<NAME>}.
 */
@Bindable
@Target({ ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface QueryFilterFormat {
    QueryFilter.Resource value();
}