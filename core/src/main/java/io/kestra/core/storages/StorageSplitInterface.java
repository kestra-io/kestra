package io.kestra.core.storages;

import io.kestra.core.models.property.Property;
import io.swagger.v3.oas.annotations.media.Schema;

public interface StorageSplitInterface {
    @Schema(
        title = "Split a large file into multiple chunks with a maximum file size of `bytes`.",
        description = "Can be provided as a string in the format \"10MB\" or \"200KB\", or the number of bytes. " +
            "This allows you to process large files, slit them into smaller chunks by lines and process them in parallel. For example, MySQL by default limits the size of a query size to 16MB per query. Trying to use a bulk insert query with input data larger than 16MB will fail. Splitting the input data into smaller chunks is a common strategy to circumvent this limitation. By dividing a large data set into chunks smaller than the `max_allowed_packet` size (e.g., 10MB), you can insert the data in multiple smaller queries. This approach not only helps to avoid hitting the query size limit but can also be more efficient and manageable in terms of memory utilization, especially for very large datasets. In short, by splitting the file by bytes, you can bulk-insert smaller chunks of e.g. 10MB in parallel to avoid this limitation."
    )
    Property<String> getBytes();

    @Schema(
        title = "Split a file into a fixed number of partitioned files. For example, if you have a file with 1000 lines and you set `partitions` to 10, the file will be split into 10 files with 100 lines each."
    )
    Property<Integer> getPartitions();

    @Schema(
        title = "A number of rows per batch. The file will then be split into chunks with that maximum number of rows."
    )
    Property<Integer> getRows();

    @Schema(
        title = "The separator used to split a file into chunks. By default, it's a newline `\\n` character. If you are on Windows, you might want to use `\\r\\n` instead.",
        defaultValue = "\\n"
    )
    Property<String> getSeparator();
}
