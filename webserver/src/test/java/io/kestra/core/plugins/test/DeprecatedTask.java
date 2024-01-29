package io.kestra.core.plugins.test;

import io.kestra.core.models.annotations.PluginProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Deprecated
public class DeprecatedTask extends SuperclassTask {
    @NotBlank
    @PluginProperty(dynamic = true)
    @Deprecated
    private String additionalProperty;
}
