package io.kestra.core.runners;

import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.utils.IdUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.throwBiConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class FilesService {
     public static Map<String, String> inputFiles(RunContext runContext, Object inputs) throws Exception {
         return FilesService.inputFiles(runContext, Collections.emptyMap(), inputs);
     }

     public static Map<String, String> inputFiles(RunContext runContext, Map<String, Object> additionalVars, Object inputs) throws Exception {
         Logger logger = runContext.logger();

         Map<String, String> inputFiles = new HashMap<>(inputs == null ? Map.of() : PluginUtilsService.transformInputFiles(
             runContext,
             additionalVars,
             inputs
         ));

         inputFiles
             .forEach(throwBiConsumer((fileName, input) -> {
                 var file = new File(runContext.workingDir().path().toString(), runContext.render(fileName, additionalVars));

                 if (!file.getParentFile().exists()) {
                     //noinspection ResultOfMethodCallIgnored
                     file.getParentFile().mkdirs();
                 }

                 if (input == null) {
                    file.createNewFile();
                 } else {
                     if (input.startsWith("kestra://")) {
                         try (var is = runContext.storage().getFile(URI.create(input));
                              var out = new FileOutputStream(file)) {
                             IOUtils.copyLarge(is, out);
                         }
                     } else {
                         Files.write(file.toPath(), input.getBytes());
                     }
                 }
             }));

         if (logger.isTraceEnabled()) {
             logger.trace("Provided {} input(s).", inputFiles.size());
         }

         return inputFiles;
     }

    public static Map<String, URI> outputFiles(RunContext runContext, List<String> outputs) throws Exception {
        List<String> renderedOutputs = outputs != null ? runContext.render(outputs) : null;
        List<Path> allFilesMatching = runContext.workingDir().findAllFilesMatching(renderedOutputs);
        var outputFiles = allFilesMatching.stream()
            .map(throwFunction(path -> new AbstractMap.SimpleEntry<>(
                runContext.workingDir().path().relativize(path).toString(),
                runContext.storage().putFile(path.toFile(), resolveUniqueNameForFile(path))
            )))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (runContext.logger().isTraceEnabled()) {
            runContext.logger().trace("Captured {} output(s).", allFilesMatching.size());
        }

        return outputFiles;
    }

    private static String resolveUniqueNameForFile(final Path path) {
        return IdUtils.from(path.toString()) + "-" + path.toFile().getName();
    }
}
