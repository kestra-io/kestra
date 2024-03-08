package io.kestra.core.models.script;

import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Helper class for script runners and script tasks.
 */
public final class ScriptService {
    private static final Pattern INTERNAL_STORAGE_PATTERN = Pattern.compile("(kestra:\\/\\/[-a-zA-Z0-9%._\\+~#=/]*)");

    private ScriptService() {
    }

    public static String replaceInternalStorage(RunContext runContext, @Nullable String command) throws IOException {
        if (command == null) {
            return "";
        }

        return INTERNAL_STORAGE_PATTERN
            .matcher(command)
            .replaceAll(throwFunction(matchResult -> saveOnLocalStorage(runContext, matchResult.group())));
    }

    public static List<String> uploadInputFiles(RunContext runContext, List<String> commands) throws IOException {
        return commands
            .stream()
            .map(throwFunction(s -> replaceInternalStorage(runContext, s)))
            .collect(Collectors.toList());

    }

    private static String saveOnLocalStorage(RunContext runContext, String uri) throws IOException {
        try(InputStream inputStream = runContext.storage().getFile(URI.create(uri))) {
            Path path = runContext.tempFile();

            IOUtils.copyLarge(inputStream, new FileOutputStream(path.toFile()));

            return path.toString();
        }
    }

    public static Map<String, URI> uploadOutputFiles(RunContext runContext, Path outputDir) throws IOException {
        // upload output files
        Map<String, URI> uploaded = new HashMap<>();

        try (Stream<Path> walk = Files.walk(outputDir)) {
            walk
                .filter(Files::isRegularFile)
                .filter(path -> !path.startsWith("."))
                .forEach(throwConsumer(path -> {
                    String filename = outputDir.relativize(path).toString();

                    uploaded.put(
                        filename,
                        runContext.storage().putFile(path.toFile(), filename)
                    );
                }));
        }

        return uploaded;
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, String command) {
        return scriptCommands(interpreter, beforeCommands, List.of(command));
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, List<String> commands) {
        ArrayList<String> commandsArgs = new ArrayList<>(interpreter);
        commandsArgs.add(
            Stream
                .concat(
                    ListUtils.emptyOnNull(beforeCommands).stream(),
                    commands.stream()
                )
                .collect(Collectors.joining(System.lineSeparator()))
        );

        return commandsArgs;
    }
}
