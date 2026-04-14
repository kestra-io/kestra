package io.kestra.core.validations;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Minimal task used in tests to verify that @PluginProperty(secret=true) fields with plain-text
 * values produce a warning (not a hard validation error).
 */
@SuperBuilder
@NoArgsConstructor
@Getter
public class SecretFieldTask extends Task {
    @PluginProperty(secret = true)
    private String secretField;
}
