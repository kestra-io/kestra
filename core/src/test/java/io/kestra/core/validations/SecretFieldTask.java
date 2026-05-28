package io.kestra.core.validations;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** Test task with a secret property, used to verify secret field warning behavior. */
@SuperBuilder
@NoArgsConstructor
@Getter
public class SecretFieldTask extends Task {
    @PluginProperty(secret = true)
    private String secretField;
}
