package io.kestra.core.runners;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import io.kestra.core.models.property.URIFetcher;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.utils.IdUtils;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class FilesService {
    public static Map<String, String> inputFiles(RunContext runContext, Object inputs) throws Exception {
        return FilesService.inputFiles(runContext, Collections.emptyMap(), inputs);
    }

    public static Map<String, String> inputFiles(RunContext runContext, Map<String, Object> additionalVars, Object inputs) throws Exception {
        Logger logger = runContext.logger();

        Map<String, String> inputFiles = new HashMap<>(
            inputs == null ? Map.of()
                : PluginUtilsService.transformInputFiles(
                    runContext,
                    additionalVars,
                    inputs
                )
        );

        materializeInputFiles(runContext, additionalVars, inputFiles);

        if (logger.isTraceEnabled()) {
            logger.trace("Provided {} input(s).", inputFiles.size());
        }

        return inputFiles;
    }

    /**
     * Writes already-rendered {@code fileName -> value} input files into the working directory.
     * <p>
     * Split out of {@link #inputFiles(RunContext, Map, Object)} so a caller that already ran
     * {@link PluginUtilsService#transformInputFiles} once (e.g. to split off {@code kestra://} entries for
     * a task runner supporting direct input files) can materialize the rest without rendering them again.
     */
    public static void materializeInputFiles(RunContext runContext, Map<String, Object> additionalVars, Map<String, String> inputFiles) throws Exception {
        inputFiles
            .forEach(throwBiConsumer((fileName, input) ->
            {
                var file = runContext.workingDir().resolve(Path.of(runContext.render(fileName, additionalVars))).toFile();

                if (!file.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.getParentFile().mkdirs();
                }

                if (input == null) {
                    if (!file.createNewFile()) {
                        throw new RuntimeException("Unable to create the file: " + file.getName());
                    }
                } else {
                    if (URIFetcher.supports(input)) {
                        var uri = URIFetcher.of(input);
                        try (
                            var is = new BufferedInputStream(uri.fetch(runContext), FileSerde.BUFFER_SIZE);
                            var out = new FileOutputStream(file)
                        ) {
                            IOUtils.copyLarge(is, out);
                        }
                    } else {
                        Files.write(file.toPath(), input.getBytes());
                    }
                }
            }));
    }

    /**
     * Downloads already-resolved {@code kestra://} input files into the working directory, keyed by the
     * relative path they should end up at. Used by a task runner supporting direct input files
     * ({@link io.kestra.core.models.tasks.runners.RemoteRunnerInterface#supportsDirectInputFiles()}) as the
     * fallback path when it can't (or decided not to) grant direct access to internal storage for this run.
     */
    public static void materializeInputFiles(RunContext runContext, Map<String, URI> directInputFiles) throws Exception {
        for (var entry : Optional.ofNullable(directInputFiles).orElse(Map.of()).entrySet()) {
            File file = runContext.workingDir().resolve(Path.of(entry.getKey())).toFile();

            if (!file.getParentFile().exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.getParentFile().mkdirs();
            }

            try (
                var is = new BufferedInputStream(runContext.storage().getFile(entry.getValue()), FileSerde.BUFFER_SIZE);
                var out = new FileOutputStream(file)
            ) {
                IOUtils.copyLarge(is, out);
            }
        }
    }

    public static Map<String, URI> outputFiles(RunContext runContext, List<String> outputs) throws Exception {
        List<String> renderedOutputs = outputs != null ? runContext.render(outputs) : null;
        List<Path> allFilesMatching = runContext.workingDir().findAllFilesMatching(renderedOutputs);
        var outputFiles = allFilesMatching.stream()
            .map(
                throwFunction(
                    path -> new AbstractMap.SimpleEntry<>(
                        runContext.workingDir().path().relativize(path).toString(),
                        runContext.storage().putFile(path.toFile(), resolveUniqueNameForFile(path))
                    )
                )
            )
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (runContext.logger().isTraceEnabled()) {
            runContext.logger().trace("Captured {} output file(s).", allFilesMatching.size());
        }

        return outputFiles;
    }

    private static String resolveUniqueNameForFile(final Path path) {
        String filename = path.getFileName().toString().replace(' ', '+');
        return IdUtils.from(path.toString()) + "-" + filename;
    }
}
