package io.kestra.repository.elasticsearch;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.repository.type", value = "elasticsearch")
public @interface ElasticSearchRepositoryEnabled {
}