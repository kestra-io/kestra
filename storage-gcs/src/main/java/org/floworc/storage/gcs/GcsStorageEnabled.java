package org.floworc.storage.gcs;

import io.micronaut.context.annotation.Requires;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Requires(property = "floworc.storage.type", value = "gcs")
public @interface GcsStorageEnabled {
}