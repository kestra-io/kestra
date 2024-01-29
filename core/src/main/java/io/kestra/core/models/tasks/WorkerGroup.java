package io.kestra.core.models.tasks;

import io.kestra.core.validations.WorkerGroupValidation;
import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Pattern;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
@WorkerGroupValidation
public class WorkerGroup {
    @Pattern(regexp="^[a-zA-Z0-9][a-zA-Z0-9_-]*")
    private String key;
}
