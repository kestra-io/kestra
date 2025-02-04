package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import jakarta.validation.Valid;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class NamespaceFiles {
    @Schema(
        title = "Whether to enable namespace files to be loaded into the working directory. If explicitly set to `true` in a task, it will load all [Namespace Files](https://kestra.io/docs/developer-guide/namespace-files) into the task's working directory. Note that this property is by default set to `true` so that you can specify only the `include` and `exclude` properties to filter the files to load without having to explicitly set `enabled` to `true`."
    )
    @Builder.Default
    private Property<Boolean> enabled = Property.of(true);

    @Schema(
        title = "A list of filters to include only matching glob patterns. This allows you to only load a subset of the [Namespace Files](https://kestra.io/docs/developer-guide/namespace-files) into the working directory."
    )
    @Valid
    private Property<List<String>> include;

    @Schema(
        title = "A list of filters to exclude matching glob patterns. This allows you to exclude a subset of the [Namespace Files](https://kestra.io/docs/developer-guide/namespace-files) from being downloaded at runtime. You can combine this property together with `include` to only inject a subset of files that you need into the task's working directory."
    )
    @Valid
    private Property<List<String>> exclude;
}
