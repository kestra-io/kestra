package io.kestra.core.tasks.scripts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.validation.constraints.NotNull;

import static io.kestra.core.utils.Rethrow.throwConsumer;

abstract public class BashService {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Pattern PATTERN = Pattern.compile("^::(\\{.*})::$");

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars
    ) throws IOException {
        return BashService.createOutputFiles(tempDirectory, outputFiles, additionalVars, false);
    }

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars,
        Boolean isDir
    ) throws IOException {
        List<String> outputs = new ArrayList<>();

        if (outputFiles != null && outputFiles.size() > 0) {
            outputs.addAll(outputFiles);
        }

        Map<String, String> result = new HashMap<>();
        if (outputs.size() > 0) {
            outputs
                .forEach(throwConsumer(s -> {
                    BashService.validFilename(s);
                    File tempFile;

                    if (isDir) {
                        tempFile = Files.createTempDirectory(tempDirectory, s + "_").toFile();
                    } else {
                        tempFile = File.createTempFile(s + "_", null, tempDirectory.toFile());
                    }

                    result.put(s, "{{workingDir}}/" + tempFile.getName());
                }));

            if (!isDir) {
                additionalVars.put("temp", result);
            }
            additionalVars.put(isDir ? "outputDirs": "outputFiles", result);
        }

        return result;
    }

    private static void validFilename(String s) {
        if (s.startsWith("./") || s.startsWith("..") || s.startsWith("/")) {
            throw new IllegalArgumentException("Invalid outputFile (only relative path is supported) " +
                "for path '" + s + "'"
            );
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> transformInputFiles(RunContext runContext, @NotNull Object inputFiles) throws IllegalVariableEvaluationException, JsonProcessingException {
        if (inputFiles instanceof Map) {
            return (Map<String, String>) inputFiles;
        } else if (inputFiles instanceof String) {
            final TypeReference<Map<String, String>> reference = new TypeReference<>() {};

            return JacksonMapper.ofJson(false).readValue(
                runContext.render((String) inputFiles),
                reference
            );
        } else {
            throw new IllegalVariableEvaluationException("Invalid `files` properties with type '" + (inputFiles != null ? inputFiles.getClass() : "null") + "'");
        }
    }


    public static void createInputFiles(
        RunContext runContext,
        Path workingDirectory,
        Map<String, String> inputFiles,
        Map<String, Object> additionalVars
    ) throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        if (inputFiles != null && inputFiles.size() > 0) {
            for (String fileName : inputFiles.keySet()) {
                String finalFileName = runContext.render(fileName);

                BashService.validFilename(finalFileName);

                File file = new File(fileName);

                // path with "/", create the subfolders
                if (file.getParent() != null) {
                    Path subFolder = Paths.get(
                        workingDirectory.toAbsolutePath().toString(),
                        new File(finalFileName).getParent()
                    );

                    if (!subFolder.toFile().exists()) {
                        Files.createDirectories(subFolder);
                    }
                }

                String filePath = workingDirectory + "/" + finalFileName;
                String render = runContext.render(inputFiles.get(fileName), additionalVars);

                if (render.startsWith("kestra://")) {
                    try (
                        InputStream inputStream = runContext.uriToInputStream(new URI(render));
                        OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(filePath))
                    ) {
                        int byteRead;
                        while ((byteRead = inputStream.read()) != -1) {
                            outputStream.write(byteRead);
                        }
                        outputStream.flush();
                    }
                } else {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                        writer.write(render);
                    }
                }
            }
        }
    }
    public static Map<String, Object> parseOut(String line, Logger logger, RunContext runContext)  {
        Matcher m = PATTERN.matcher(line);
        Map<String, Object> outputs = new HashMap<>();

        if (m.find()) {
            try {
                BashCommand<?> bashCommand = MAPPER.readValue(m.group(1), BashCommand.class);

                if (bashCommand.getOutputs() != null) {
                    outputs.putAll(bashCommand.getOutputs());
                }

                if (bashCommand.getMetrics() != null) {
                    bashCommand.getMetrics().forEach(runContext::metric);
                }
            }
            catch (JsonProcessingException e) {
                logger.warn("Invalid outputs '{}'", e.getMessage(), e);
            }
        }

        return outputs;
    }

    @NoArgsConstructor
    @Data
    public static class BashCommand <T> {
        private Map<String, Object> outputs;
        private List<AbstractMetricEntry<T>> metrics;
    }
}
