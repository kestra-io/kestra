package io.kestra.core.models.storage;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class FileMetas {
    @NotNull
    long size;
}
