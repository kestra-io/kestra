package io.kestra.core.models.storage;

import lombok.Builder;
import lombok.Value;

import javax.validation.constraints.NotNull;

@Value
@Builder
public class FileMetas {
    @NotNull
    long size;
}
