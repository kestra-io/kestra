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
        title = "Split a large file into multiple chunks with a maximum file size of `bytes`.",
        description = "Can be provided as a string in the format \"10MB\" or \"200KB\", or the number of bytes. " +
            "This allows you to process large files, slit them into smaller chunks by lines and process them in parallel. For example, MySQL by default limits the size of a query size to 16MB per query. Trying to use a bulk insert query with input data larger than 16MB will fail. Splitting the input data into smaller chunks is a common strategy to circumvent this limitation. By dividing a large data set into chunks smaller than the `max_allowed_packet` size (e.g., 10MB), you can insert the data in multiple smaller queries. This approach not only helps to avoid hitting the query size limit but can also be more efficient and manageable in terms of memory utilization, especially for very large datasets. In short, by splitting the file by bytes, you can bulk-insert smaller chunks of e.g. 10MB in parallel to avoid this limitation."
    )
    @PluginProperty(dynamic = true)
    String getBytes();

    @Schema(
        title = "Split a file into a fixed number of partitioned files. For example, if you have a file with 1000 lines and you set `partitions` to 10, the file will be split into 10 files with 100 lines each."
    )
    @PluginProperty(dynamic = true)
    Integer getPartitions();

    @Schema(
        title = "A number of rows per batch. The file will then be split into chunks with that maximum number of rows."
    )
    @PluginProperty(dynamic = true)
    Integer getRows();

    @Schema(
        title = "The separator used to split a file into chunks. By default, it's a newline `\\n` character. If you are on Windows, you might want to use `\\r\\n` instead.",
        defaultValue = "\\n"
    )
    @PluginProperty
    String getSeparator();
}
