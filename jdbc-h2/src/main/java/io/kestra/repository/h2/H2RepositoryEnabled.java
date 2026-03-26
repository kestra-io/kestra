package io.kestra.repository.h2;

import java.lang.annotation.*;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.PACKAGE, ElementType.TYPE })
@Requires(property = "kestra.repository.type", pattern = "h2|memory")
@DefaultImplementation
public @interface H2RepositoryEnabled {
}