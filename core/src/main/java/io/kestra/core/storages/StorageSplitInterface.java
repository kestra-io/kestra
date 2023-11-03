package io.kestra.core.storages;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import io.kestra.core.annotations.Retryable;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.utils.Slugify;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface StorageSplitInterface {
    @Schema(
        title = "Split by file size.",
        description = "Can be provided as a string like \"10MB\" or \"200KB\", or the number of bytes. " +
            "Since we divide storage per line, it's not an hard requirements and files can be a larger."
    )
    @PluginProperty(dynamic = true)
    String getBytes();

    @Schema(
        title = "Split by a fixed number of files."
    )
    @PluginProperty(dynamic = true)
    Integer getPartitions();

    @Schema(
        title = "Split by file rows count."
    )
    @PluginProperty(dynamic = true)
    Integer getRows();

    @Schema(
        title = "The separator to used between rows"
    )
    @PluginProperty
    String getSeparator();
}
