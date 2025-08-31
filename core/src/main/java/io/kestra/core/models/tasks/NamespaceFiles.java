package io.kestra.core.models.tasks;

import io.kestra.core.models.property.Property;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import jakarta.validation.Valid;
import lombok.extern.jackson.Jacksonized;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
@Jacksonized
public class NamespaceFiles {
    @Schema(
        title = "Whether to enable namespace files to be loaded into the working directory. If explicitly set to `true` in a task, it will load all [Namespace Files](https://kestra.io/docs/developer-guide/namespace-files) into the task's working directory. Note that this property is by default set to `true` so that you can specify only the `include` and `exclude` properties to filter the files to load without having to explicitly set `enabled` to `true`."
    )
    @Builder.Default
    private Property<Boolean> enabled = Property.ofValue(true);

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

    @Schema(
        title = "A list of namespaces in which searching files. The files are loaded in the namespace order, and only the latest version of a file is kept. Meaning if a file is present in the first and second namespace, only the file present on the second namespace will be loaded."
    )
    @Builder.Default
    private Property<List<String>> namespaces = Property.ofExpression("""
        ["{{flow.namespace}}"]""");

    @Schema(
        title = "Comportment of the task if a file already exist in the working directory."
    )
    @Builder.Default
    private Property<FileExistComportment> ifExists = Property.ofValue(FileExistComportment.OVERWRITE);

    @Schema(
        title = "Whether to mount file into the root of the working directory, or create a folder per namespace"
    )
    @Builder.Default
    private Property<Boolean> folderPerNamespace = Property.ofValue(false);
}
