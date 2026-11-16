package io.kestra.webserver.models.flows;

import java.util.List;

import io.micronaut.core.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KeyUsagesRequest(
    @NotNull Kind kind,
    @NotNull @Size(max = MAX_KEYS) List<@Valid Key> keys
) {
    public static final int MAX_KEYS = 100;

    public enum Kind {
        KV("kv"),
        SECRET("secret");

        private final String functionName;

        Kind(String functionName) {
            this.functionName = functionName;
        }

        public String functionName() {
            return functionName;
        }
    }

    public record Key(@Nullable String namespace, @NotBlank String key) {
    }
}
