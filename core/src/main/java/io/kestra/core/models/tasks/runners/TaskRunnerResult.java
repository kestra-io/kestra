package io.kestra.core.models.tasks.runners;

import java.net.URI;
import java.util.Map;

import io.kestra.core.models.annotations.Beta;
import io.kestra.core.models.tasks.Output;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@AllArgsConstructor
@Getter
@SuperBuilder
@NoArgsConstructor
public class TaskRunnerResult<T extends TaskRunnerDetailResult> implements Output {
    private int exitCode;

    private AbstractLogConsumer logConsumer;

    @Nullable
    private T details;

    /**
     * Output files already collected server-side by the runner (e.g. a server-side storage copy or a
     * direct stream), keyed and named like {@link io.kestra.core.runners.FilesService#outputFiles}.
     * {@code null} when the runner collected nothing itself, leaving {@code CommandsWrapper} to collect
     * output files from the local working directory as it does today.
     */
    @Beta
    @Nullable
    private Map<String, URI> outputFiles;

    public TaskRunnerResult(int exitCode, AbstractLogConsumer logConsumer) {
        this.exitCode = exitCode;
        this.logConsumer = logConsumer;
    }
}
