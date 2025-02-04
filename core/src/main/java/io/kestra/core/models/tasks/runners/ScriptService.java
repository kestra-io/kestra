package io.kestra.core.models.tasks.runners;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.utils.Slugify;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

/**
 * Helper class for task runners and script tasks.
 */
public final class ScriptService {
    private static final Pattern INTERNAL_STORAGE_PATTERN = Pattern.compile("(kestra:\\/\\/[-a-zA-Z0-9%._\\+~#=/]*)");

    // These are the three common additional variables task runners must provide for variable rendering.
    public static final String VAR_WORKING_DIR = "workingDir";
    public static final String VAR_OUTPUT_DIR = "outputDir";
    public static final String VAR_BUCKET_PATH = "bucketPath";

    // These are the three common environment variables task runners must add to the process/container that runs the script.
    public static final String ENV_WORKING_DIR = "WORKING_DIR";
    public static final String ENV_OUTPUT_DIR = "OUTPUT_DIR";
    public static final String ENV_BUCKET_PATH = "BUCKET_PATH";

    private ScriptService() {
    }

    public static String replaceInternalStorage(
        RunContext runContext,
        @Nullable String command,
        boolean replaceWithRelativePath
    ) throws IOException {
        if (command == null) {
            return "";
        }

        return INTERNAL_STORAGE_PATTERN
            .matcher(command)
            .replaceAll(throwFunction(matchResult -> {
                String localFile = saveOnLocalStorage(runContext, matchResult.group()).replace("\\", "/");

                if (!replaceWithRelativePath) {
                    return localFile;
                }

                return runContext.workingDir().path().relativize(Path.of(localFile)).toString();
            }));
    }

    public static String replaceInternalStorage(
        RunContext runContext,
        Map<String, Object> additionalVars,
        String command,
        boolean replaceWithRelativePath
    ) throws IOException, IllegalVariableEvaluationException {
        if (command == null) {
            return null;
        }

        return ScriptService.replaceInternalStorage(runContext, additionalVars, List.of(command), replaceWithRelativePath).getFirst();
    }

    public static List<String> replaceInternalStorage(
        RunContext runContext,
        Map<String, Object> additionalVars,
        List<String> commands,
        boolean replaceWithRelativePath
    ) throws IOException, IllegalVariableEvaluationException {
        return ListUtils.emptyOnNull(commands)
            .stream()
            .map(throwFunction(c -> runContext.render(c, additionalVars)))
            .map(throwFunction(c -> ScriptService.replaceInternalStorage(runContext, c, replaceWithRelativePath)))
            .toList();

    }

    public static List<String> replaceInternalStorage(
        RunContext runContext,
        List<String> commands
    ) throws IOException, IllegalVariableEvaluationException {
        return ScriptService.replaceInternalStorage(runContext, Collections.emptyMap(), commands, false);
    }

    private static String saveOnLocalStorage(RunContext runContext, String uri) throws IOException {
        Path path = runContext.workingDir().createTempFile();

        try (InputStream inputStream = runContext.storage().getFile(URI.create(uri));
            OutputStream outputStream = new FileOutputStream(path.toFile())) {
            IOUtils.copyLarge(inputStream, outputStream);

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
        return scriptCommands(interpreter, beforeCommands, List.of(command), TargetOS.LINUX);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, List<String> commands) {
        return scriptCommands(interpreter, beforeCommands, commands, TargetOS.LINUX);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, String command, TargetOS targetOS) {
        return scriptCommands(interpreter, beforeCommands, List.of(command), targetOS);
    }

    public static List<String> scriptCommands(List<String> interpreter, List<String> beforeCommands, List<String> commands, TargetOS targetOS) {
        ArrayList<String> commandsArgs = new ArrayList<>(interpreter);
        commandsArgs.add(
            Stream
                .concat(
                    ListUtils.emptyOnNull(beforeCommands).stream(),
                    commands.stream()
                )
                .collect(Collectors.joining(targetOS.lineSeparator))
        );

        return commandsArgs;
    }

    /**
     * Generate a map of labels ready to be used on container or cloud resource with normalization of values.
     * See {@link #labels(RunContext, String, boolean, boolean)}
     */
    public static Map<String, String> labels(RunContext runContext, String prefix) {
        return labels(runContext, prefix, true, false);
    }

    /**
     * Generate a map of labels ready to be used on container or cloud resource.
     * If a prefix is set, label names will be generated as 'prefix/name'.
     * If normalizeValue is true, label values will normalize it based on the DNS Subdomain Names (RFC 1123) with a limit of 63 characters as used by Kubernetes.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, String> labels(RunContext runContext, String prefix, boolean normalizeValue, boolean lowerCase) {
        Map<String, String> flow = (Map<String, String>) runContext.getVariables().get("flow");
        Map<String, String> task = (Map<String, String>) runContext.getVariables().get("task");
        Map<String, String> execution = (Map<String, String>) runContext.getVariables().get("execution");
        Map<String, String> taskrun = (Map<String, String>) runContext.getVariables().get("taskrun");

        return ImmutableMap.of(
            withPrefix("namespace", prefix), normalizeValue(flow.get("namespace"), normalizeValue, lowerCase),
            withPrefix("flow-id", prefix), normalizeValue(flow.get("id"), normalizeValue, lowerCase),
            withPrefix("task-id", prefix), normalizeValue(task.get("id"), normalizeValue, lowerCase),
            withPrefix("execution-id", prefix), normalizeValue(execution.get("id"), normalizeValue, lowerCase),
            withPrefix("taskrun-id", prefix), normalizeValue(taskrun.get("id"), normalizeValue, lowerCase),
            withPrefix("taskrun-attempt", prefix), normalizeValue(String.valueOf(taskrun.get("attemptsCount")), normalizeValue, lowerCase)
        );
    }

    private static String withPrefix(String name, String prefix) {
        return prefix == null ? name : prefix + name;
    }

    private static String normalizeValue(String value, boolean normalizeValue, boolean lowerCase) {
        if (!normalizeValue) {
            return value;
        }

        return lowerCase ? normalize(value).toLowerCase() : normalize(value);
    }

    /**
     * Normalize a String based on the DNS Subdomain Names (RFC 1123) with a limit of 63 characters as used by Kubernetes.
     */
    public static String normalize(String string) {
        if (string == null) {
            return null;
        }

        if (string.length() > 63) {
            string = string.substring(0, 63);
        }

        string = StringUtils.stripEnd(string, "-");
        string = StringUtils.stripEnd(string, ".");
        string = StringUtils.stripEnd(string, "_");

        return string;
    }

    /**
     * Create a job name like {namespace}-{flowId}-{taskId}-{random} with random being 5 alphanumerical characters.
     * The Job name will be normalized based on the DNS Subdomain Names (RFC 1123) with a limit of 63 characters as used by Kubernetes
     */
    @SuppressWarnings("unchecked")
    public static String jobName(RunContext runContext) {
        Map<String, String> flow = (Map<String, String>) runContext.getVariables().get("flow");
        Map<String, String> task = (Map<String, String>) runContext.getVariables().get("task");

        String name = Slugify.of(String.join(
            "-",
            flow.get("namespace"),
            flow.get("id"),
            task.get("id")
        ));
        String normalized = normalizeValue(name, true, true);
        if (normalized.length() > 58) {
            normalized = normalized.substring(0, 57);
        }

        // we add a suffix of 5 chars, this should be enough as it's the standard k8s way
        String suffix = RandomStringUtils.secure().nextAlphanumeric(5).toLowerCase();
        return normalized + "-" + suffix;
    }
}
