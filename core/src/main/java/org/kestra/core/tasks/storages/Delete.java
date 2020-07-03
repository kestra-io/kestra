package org.kestra.core.tasks.storages;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.annotations.OutputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.RunContext;
import org.kestra.core.storages.StorageInterface;

import java.net.URI;
import java.util.NoSuchElementException;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Delete a file from internal storage."
)
@Example(
    code = {
        "uri: \"kestra://long/url/file.txt\""
    }
)
public class Delete extends Task implements RunnableTask<Delete.Output> {
    @InputProperty(
        description = "the file to delete",
        body = {
            "Must be a `kestra://` storage url"
        },
        dynamic = false
    )
    private URI uri;

    @InputProperty(
        description = "raise an error if the file is not found",
        dynamic = false
    )
    @Builder.Default
    private Boolean errorOnMissing = false;

    @Override
    public Delete.Output run(RunContext runContext) throws Exception {
        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);

        boolean delete = storageInterface.delete(this.uri);

        if (errorOnMissing && !delete) {
            throw new NoSuchElementException("Unable to find file '" + this.uri + "'");
        }

        return Delete.Output.builder()
            .deleted(delete)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        @OutputProperty(
            description = "Returns the version ID of the delete marker created as a result of the DELETE operation."
        )
        private final Boolean deleted;
    }
}
