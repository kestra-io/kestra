package io.kestra.core.models.collectors;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class Result {
    private final String uuid;
    private final int status;
}
