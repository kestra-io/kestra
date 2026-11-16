package io.kestra.webserver.models.flows;

import java.util.List;

import io.micronaut.core.annotation.Nullable;

public record KeyUsagesResponse(List<KeyUsage> results) {

    public record KeyUsage(@Nullable String namespace, String key, int flows, boolean truncated) {
    }
}
