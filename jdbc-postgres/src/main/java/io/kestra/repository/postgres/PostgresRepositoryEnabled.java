package io.kestra.repository.postgres;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.repository.type", value = "postgres")
@DefaultImplementation
public @interface PostgresRepositoryEnabled {
}