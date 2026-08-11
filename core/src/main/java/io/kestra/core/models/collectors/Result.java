package io.kestra.core.models.collectors;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@SuperBuilder
@Getter
@Jacksonized
public class Result {
    private final String uuid;
    private final int status;
}
