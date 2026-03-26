package io.kestra.core.models.collectors;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class Result {
    private final String uuid;
    private final int status;
}
