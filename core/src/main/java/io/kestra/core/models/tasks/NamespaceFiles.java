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
        title = "Whether to enable namespace files to be loaded into the working directory"
    )
    @PluginProperty
    @Builder.Default
    private Boolean enabled = true;

    @Schema(
        title = "A list of filters to include only matching glob patterns"
    )
    @PluginProperty
    @Valid
    private List<String> include;

    @Schema(
        title = "A list of filters to exclude matching glob patterns"
    )
    @PluginProperty
    @Valid
    private List<String> exclude;
}
