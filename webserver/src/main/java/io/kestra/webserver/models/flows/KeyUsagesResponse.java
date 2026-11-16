package io.kestra.webserver.models.flows;

import io.micronaut.core.annotation.Nullable;

public record KeyUsagesResponse(@Nullable String namespace, String key, int flows, boolean truncated) {
}
