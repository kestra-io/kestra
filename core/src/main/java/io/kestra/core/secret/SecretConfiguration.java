package io.kestra.core.secret;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;
import jakarta.validation.constraints.NotNull;

/**
 * Configuration of the environment-based secret backend.
 *
 * @param encoding how the value of a {@code SECRET_*} environment variable is encoded
 */
@ConfigurationProperties("kestra.secret")
public record SecretConfiguration(
    @NotNull @Bindable(defaultValue = "BASE64") SecretEncoding encoding) {
}
