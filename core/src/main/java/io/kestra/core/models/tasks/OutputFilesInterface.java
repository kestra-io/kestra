package io.kestra.core.models.tasks;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public interface OutputFilesInterface {
    @Schema(
        title = "The files from the local filesystem to send to Kestra's internal storage.",
        description = "Must be a list of [glob](https://en.wikipedia.org/wiki/Glob_(programming)) expressions relative to the current working directory, some examples: `my-dir/**`, `my-dir/*/**` or `my-dir/my-file.txt`."
    )
    Property<List<String>> getOutputFiles();
}
