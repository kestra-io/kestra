package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import javax.validation.Valid;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class NamespaceFiles {
    @Schema(
        title = "Enabled injections of namespace files"
    )
    @PluginProperty
    @Builder.Default
    private Boolean enabled = true;

    @Schema(
        title = "Filter to include only matching glob patterns"
    )
    @PluginProperty
    @Valid
    private List<String> include;

    @Schema(
        title = "Filter to exclude some matching glob patterns"
    )
    @PluginProperty
    @Valid
    private List<String> exclude;
}
