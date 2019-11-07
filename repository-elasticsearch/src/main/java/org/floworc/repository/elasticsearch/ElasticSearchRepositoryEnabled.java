package org.floworc.repository.elasticsearch;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "floworc.repository.type", value = "elasticsearch")
public @interface ElasticSearchRepositoryEnabled {
}