package io.kestra.core.models.tasks;

import io.kestra.core.validations.WorkerGroupValidation;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Pattern;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@WorkerGroupValidation
public class WorkerGroup {
    @Pattern(regexp="[a-zA-Z0-9_-]+")
    private String key;
}
