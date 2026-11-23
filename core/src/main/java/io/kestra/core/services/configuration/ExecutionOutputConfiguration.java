package io.kestra.core.services.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration of the execution outputs.
 *
 * @param limit the size limit, in bytes, above which an execution output is stored inside the internal storage
 *              instead of the database. A negative value disables the internal storage.
 */
@ConfigurationProperties("kestra.execution.outputs")
public record ExecutionOutputConfiguration(@Bindable(defaultValue = "-1") Integer limit) {
}
