package io.kestra.plugin.core.storage;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.core.services.StorageService;
import io.kestra.core.storages.StorageSplitInterface;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.net.URI;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Split a file from Kestra internal storage.",
    description = """
        Splits an input file by size (`bytes`), line count (`rows`), partitions, or regex grouping (first capture group), emitting new files in internal storage. Optional `separator` inserted between grouped lines.

        Provide exactly one split strategy at a time."""
)
@Plugin(
    examples = {
        @Example(
            title = "Split a file by size.",
            full = true,
            code = """
                id: split_bytes
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: split
                    type: io.kestra.plugin.core.storage.Split
                    from: "{{ outputs.download.uri }}"
                    bytes: 5KB
            """
        ),
        @Example(
            title = "Split a file by rows count.",
            full = true,
            code = """
                id: split_rows
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: split
                    type: io.kestra.plugin.core.storage.Split
                    from: "{{ outputs.download.uri }}"
                    rows: 10
            """
        ),
        @Example(
            title = "Split a file in a defined number of partitions.",
            full = true,
            code = """
                id: split_partitions
                namespace: company.team

                tasks:
                  - id: download
                    type: io.kestra.plugin.core.http.Download
                    uri: https://huggingface.co/datasets/kestra/datasets/raw/main/csv/orders.csv

                  - id: split
                    type: io.kestra.plugin.core.storage.Split
                    from: "{{ outputs.download.uri }}"
                    partitions: 4
            """
        ),
        @Example(
            title = "Split a file by regex pattern - group lines by log level.",
            full = true,
            code = """
                id: storage_tasks
                namespace: company.team

                tasks:
                  - id: generate_logs
                    type: io.kestra.plugin.scripts.shell.Commands
                    commands:
                      - echo "INFO - wow\nERROR - no\nINFO - ok" > logs.txt
                    outputFiles:
                      - logs.txt

                  - id: split
                    type: io.kestra.plugin.core.storage.Split
                    from: "{{ outputs.echo.outputFiles['logs.txt'] }}"
                    regexPattern: "^(\\w+)"
            """
        ),
    },
    aliases = "io.kestra.core.tasks.storages.Split"
)
public class Split extends Task implements RunnableTask<Split.Output>, StorageSplitInterface {
    @Schema(
        title = "The file to be split"
    )
    @NotNull
    @PluginProperty(internalStorageURI = true)
    private Property<String> from;

    private Property<String> bytes;

    private Property<Integer> partitions;

    private Property<Integer> rows;

    @Schema(
        title = "Split file by regex pattern. Lines are grouped by the first capture group value.",
        description = """
        A regular expression pattern with a capture group. Lines matching this pattern will be grouped by the captured value. For example, `^(\\w+)` will group lines by the first word extracted from the file.
        """
    )
    @PluginProperty(dynamic = true)
    private Property<String> regexPattern;

    @Builder.Default
    private Property<String> separator = Property.ofValue("\n");

    @Override
    public Split.Output run(RunContext runContext) throws Exception {
        URI from = new URI(runContext.render(this.from).as(String.class).orElseThrow());

        return Split.Output.builder()
            .uris(StorageService.split(runContext, this, from))
            .build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The URIs of split files in Kestra's internal storage"
        )
        private final List<URI> uris;
    }
}
