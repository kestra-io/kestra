package io.kestra.core.plugins.test;

import io.kestra.core.models.annotations.PluginProperty;

import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

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
