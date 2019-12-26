package org.kestra.storage.minio;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "kestra.storage.type", value = "minio")
public @interface MinioStorageEnabled {
}