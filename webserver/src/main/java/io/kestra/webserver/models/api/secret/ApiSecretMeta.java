package io.kestra.webserver.models.api.secret;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ApiSecretMeta {
    private final String key;

    public ApiSecretMeta(
        @NotNull
        @Parameter(
            name = "key",
            description = "The key of secret.",
            required = true)
        String key
    ) {
        this.key = key;
    }
}