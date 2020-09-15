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
        dynamic = true
    )
    private String uri;

    @InputProperty(
        description = "raise an error if the file is not found",
        dynamic = false
    )
    @Builder.Default
    private Boolean errorOnMissing = false;

    @Override
    public Delete.Output run(RunContext runContext) throws Exception {
        StorageInterface storageInterface = runContext.getApplicationContext().getBean(StorageInterface.class);
        URI render = URI.create(runContext.render(this.uri));

        boolean delete = storageInterface.delete(render);

        if (errorOnMissing && !delete) {
            throw new NoSuchElementException("Unable to find file '" + render + "'");
        }

        return Output.builder()
            .uri(render)
            .deleted(delete)
            .build();
    }

    @Builder
    @Getter
    public static class Output implements org.kestra.core.models.tasks.Output {
        @OutputProperty(
            description = "The deleted "
        )
        private final URI uri;

        @OutputProperty(
            description = "If the files was really deleted"
        )
        private final Boolean deleted;
    }
}
