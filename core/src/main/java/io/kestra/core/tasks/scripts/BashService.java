package io.kestra.core.tasks.scripts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
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

import static io.kestra.core.utils.Rethrow.throwConsumer;

abstract public class BashService {
    protected static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Pattern PATTERN = Pattern.compile("^::(\\{.*\\})::$");

    public static List<String> finalCommandsWithInterpreter(
        String interpreter,
        String[] interpreterArgs,
        String commandAsString,
        Path workingDirectory
    ) throws IOException {
        // build the final commands
        List<String> commandsWithInterpreter = new ArrayList<>(Collections.singletonList(interpreter));

        // https://www.in-ulm.de/~mascheck/various/argmax/ MAX_ARG_STRLEN (131072)
        if (commandAsString.length() > 131072) {
            File bashTempFiles = File.createTempFile("bash", ".sh", workingDirectory.toFile());
            Files.write(bashTempFiles.toPath(), commandAsString.getBytes());

            commandAsString = bashTempFiles.getAbsolutePath();
        } else {
            commandsWithInterpreter.addAll(Arrays.asList(interpreterArgs));
        }

        commandsWithInterpreter.add(commandAsString);

        return commandsWithInterpreter;
    }

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
                AbstractBash.BashCommand<?> bashCommand = MAPPER.readValue(m.group(1), AbstractBash.BashCommand.class);

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
}
