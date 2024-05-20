package io.kestra.core.models.flows;

import io.kestra.core.validations.TaskDefaultValidation;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@Introspected
@TaskDefaultValidation
public class TaskDefault {
    @NotNull
    private final String type;

    @Builder.Default
    private final boolean forced = false;

    private final Map<String, Object> values;
}

