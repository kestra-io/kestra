package io.kestra.core.models.tasks.runners;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import io.kestra.core.models.annotations.Beta;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkingDir;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.PathMatcherPredicate;

import jakarta.annotation.Nullable;

/**
 * Collects task output files staged by a remote task runner (e.g. a cloud batch job writing to its own
 * object storage) into Kestra's internal storage, reproducing the same keys and names that
 * {@link io.kestra.core.runners.FilesService#outputFiles} and {@link ScriptService#uploadOutputFiles}
 * would produce for the same files collected from local disk.
 * <p>
 * For each object, a server-side copy is attempted first ({@link io.kestra.core.storages.Storage#copyFrom});
 * when the storage backend can't do that, the object is streamed through the worker instead.
 */
@Beta
public final class RemoteOutputFiles {
    private RemoteOutputFiles() {
    }

    /**
     * A file staged by a remote runner, not yet present in Kestra's internal storage.
     *
     * @param relativePath the file's path, relative to the task's working directory
     * @param providerUri the object's native URI on the runner's storage backend (e.g. {@code s3://bucket/key}),
     *                     used to attempt a server-side copy; {@code null} when unavailable
     * @param opener supplies a fresh stream of the object's content, used as the fallback when a server-side
     *               copy isn't possible
     */
    public record StagedOutput(String relativePath, @Nullable URI providerUri, IOSupplier<InputStream> opener) {
    }

    @FunctionalInterface
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    /**
     * The outcome of collecting a batch of {@link StagedOutput}s.
     *
     * @param outputFiles the resulting {@code outputFiles} map, keyed and named like today's local collection
     * @param copied how many objects were copied server-side
     * @param streamed how many objects were streamed through the worker
     */
    public record Result(Map<String, URI> outputFiles, int copied, int streamed) {
    }

    /**
     * @param renderedOutputFilesPatterns the task's {@code outputFiles} glob/regex patterns, already rendered
     * @param outputDirectoryEnabled whether the task has {@code outputDirs}/output-directory collection enabled
     * @param outputDirectoryRelativePath the output directory's path relative to the working directory
     *                                     (e.g. {@code TaskCommands#outputDirectoryName()}); ignored when
     *                                     {@code outputDirectoryEnabled} is {@code false}
     */
    public static Result collect(
        RunContext runContext,
        List<StagedOutput> stagedOutputs,
        @Nullable List<String> renderedOutputFilesPatterns,
        boolean outputDirectoryEnabled,
        @Nullable String outputDirectoryRelativePath
    ) throws IOException {
        Path workingDir = runContext.workingDir().path();
        Predicate<Path> outputFilesMatcher = renderedOutputFilesPatterns == null || renderedOutputFilesPatterns.isEmpty()
            ? null
            : PathMatcherPredicate.matches(workingDir, renderedOutputFilesPatterns);
        String outputDirectoryPrefix = outputDirectoryEnabled && outputDirectoryRelativePath != null
            ? outputDirectoryRelativePath.endsWith("/") ? outputDirectoryRelativePath : outputDirectoryRelativePath + "/"
            : null;

        Map<String, URI> collected = new LinkedHashMap<>();
        int copied = 0;
        int streamed = 0;

        for (StagedOutput staged : stagedOutputs) {
            if (isExcluded(staged.relativePath())) {
                continue;
            }

            String key;
            String name;
            if (outputDirectoryPrefix != null && staged.relativePath().startsWith(outputDirectoryPrefix)) {
                key = staged.relativePath().substring(outputDirectoryPrefix.length());
                ScriptService.validateStoragePath(key);
                name = key;
            } else if (outputFilesMatcher != null && outputFilesMatcher.test(workingDir.resolve(staged.relativePath()))) {
                key = staged.relativePath();
                name = uniqueName(workingDir.resolve(staged.relativePath()));
            } else {
                continue;
            }

            Optional<URI> serverSideCopy = staged.providerUri() != null
                ? runContext.storage().copyFrom(name, staged.providerUri())
                : Optional.empty();

            if (serverSideCopy.isPresent()) {
                collected.put(key, serverSideCopy.get());
                copied++;
            } else {
                try (InputStream is = staged.opener().get()) {
                    collected.put(key, runContext.storage().putFile(is, name));
                }
                streamed++;
            }
        }

        return new Result(collected, copied, streamed);
    }

    private static boolean isExcluded(String relativePath) {
        return relativePath.endsWith("/") || Path.of(relativePath).getFileName().toString().equals(WorkingDir.EXECUTION_CONTEXT_FILE_NAME);
    }

    private static String uniqueName(Path localEquivalentPath) {
        String filename = localEquivalentPath.getFileName().toString().replace(' ', '+');
        return IdUtils.from(localEquivalentPath.toString()) + "-" + filename;
    }
}
