package io.kestra.core.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.FlowService;
import jakarta.validation.constraints.NotNull;
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

import static io.kestra.core.utils.Rethrow.throwConsumer;

/**
 * @deprecated use {@link io.kestra.core.models.tasks.runners.PluginUtilsService} instead
 */
@Deprecated
abstract public class PluginUtilsService {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();
    private static final Pattern PATTERN = Pattern.compile("^::(\\{.*})::$");
    private static final TypeReference<Map<String, String>> MAP_TYPE_REFERENCE = new TypeReference<>() {};

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars
    ) throws IOException {
        return io.kestra.core.models.tasks.runners.PluginUtilsService.createOutputFiles(tempDirectory, outputFiles, additionalVars, false);
    }

    public static Map<String, String> createOutputFiles(
        Path tempDirectory,
        List<String> outputFiles,
        Map<String, Object> additionalVars,
        Boolean isDir
    ) throws IOException {
        return io.kestra.core.models.tasks.runners.PluginUtilsService.createOutputFiles(tempDirectory, outputFiles, additionalVars, isDir);
    }

    public static Map<String, String> transformInputFiles(RunContext runContext, @NotNull Object inputFiles) throws IllegalVariableEvaluationException, JsonProcessingException {
        return io.kestra.core.models.tasks.runners.PluginUtilsService.transformInputFiles(runContext, Collections.emptyMap(), inputFiles);
    }

    public static Map<String, String> transformInputFiles(RunContext runContext, Map<String, Object> additionalVars, @NotNull Object inputFiles) throws IllegalVariableEvaluationException, JsonProcessingException {
        return io.kestra.core.models.tasks.runners.PluginUtilsService.transformInputFiles(runContext, additionalVars, inputFiles);
    }


    public static void createInputFiles(
        RunContext runContext,
        Path workingDirectory,
        Map<String, String> inputFiles,
        Map<String, Object> additionalVars
    ) throws IOException, IllegalVariableEvaluationException, URISyntaxException {
        io.kestra.core.models.tasks.runners.PluginUtilsService.createInputFiles(runContext, workingDirectory, inputFiles, additionalVars);
    }

    public static Map<String, Object> parseOut(String line, Logger logger, RunContext runContext)  {
        return io.kestra.core.models.tasks.runners.PluginUtilsService.parseOut(line, logger, runContext);
    }

    /**
     * This helper method will allow gathering the execution information from a task parameters:
     * - If executionId is null, it is fetched from the runContext variables (a.k.a. current execution).
     * - If executionId is not null but namespace and flowId are null, namespace and flowId will be fetched from the runContext variables.
     * - Otherwise, all params must be set
     * It will then check that the namespace is allowed to access the target namespace.
     * <p>
     * It will throw IllegalArgumentException for any incompatible set of variables.
     */
    public static ExecutionInfo executionFromTaskParameters(RunContext runContext, String namespace, String flowId, String executionId) throws IllegalVariableEvaluationException {
        var executionInfo = io.kestra.core.models.tasks.runners.PluginUtilsService.executionFromTaskParameters(runContext, namespace, flowId, executionId);

        return new ExecutionInfo(executionInfo.tenantId(), executionInfo.namespace(), executionInfo.flowId(), executionInfo.id());
    }

    public record ExecutionInfo(String tenantId, String namespace, String flowId, String id) {}
}
