package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.exceptions.AiException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Version;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static io.kestra.core.plugins.RegisteredPlugin.*;
import static io.kestra.core.plugins.RegisteredPlugin.APP_BLOCKS_GROUP_NAME;
import static io.kestra.core.plugins.RegisteredPlugin.CHARTS_GROUP_NAME;
import static io.kestra.core.plugins.RegisteredPlugin.DATA_FILTERS_GROUP_NAME;
import static io.kestra.core.plugins.RegisteredPlugin.DATA_FILTERS_KPI_GROUP_NAME;

@Slf4j
@RequiredArgsConstructor
public class FlowAiCopilot {
    public static final String ALREADY_VALID_FLOW = "This flow already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    public static final String NON_FLOW_REQUEST_ERROR = "I can only assist with creating Kestra flows.";
    public static final String UNABLE_TO_GENERATE_FLOW_ERROR = "The prompt did not provide enough information to generate a valid flow. Please clarify your request.";
    private static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_FLOW, NON_FLOW_REQUEST_ERROR, UNABLE_TO_GENERATE_FLOW_ERROR);

    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME,
        SECRETS_GROUP_NAME,
        APPS_GROUP_NAME,
        APP_BLOCKS_GROUP_NAME,
        CHARTS_GROUP_NAME,
        DATA_FILTERS_GROUP_NAME,
        DATA_FILTERS_KPI_GROUP_NAME
    );

    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final PluginRegistry pluginRegistry;
    private final String fallbackPluginVersion;

    private List<String> mostRelevantPlugins(PluginFinder pluginFinder, String userPrompt) {
        Map<String, String> descriptionByType = pluginRegistry.plugins().stream()
            .sorted(Comparator.comparing(p -> Version.of(Optional.ofNullable(p.version()).orElse(fallbackPluginVersion))))
            .flatMap(plugin -> plugin.allClassGrouped().entrySet().stream().filter(e -> !EXCLUDED_PLUGIN_TYPES.contains(e.getKey())).map(Map.Entry::getValue).flatMap(Collection::stream))
            .map(clazz -> Map.entry(clazz.getName(), Optional.ofNullable(((Class<?>) clazz).getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class))))
            .filter(e -> !e.getValue().map(io.swagger.v3.oas.annotations.media.Schema::deprecated).orElse(false))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue()
                        .map(io.swagger.v3.oas.annotations.media.Schema::title)
                        .orElse(""),
                    (existing, replacement) -> existing // In case of duplicates, keep the first one as it's the most recent version
                )
            );
        String serializedPlugins;
        try {
            serializedPlugins = JacksonMapper.ofJson().writeValueAsString(descriptionByType.entrySet().stream().map(e ->
                Map.of("type", e.getKey(), "description", e.getValue())
            ).toList());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize plugin types for Gemini AI agent", e);
            serializedPlugins = "[]";
        }

        var mostRelevantPlugins = pluginFinder.findPlugins(serializedPlugins, userPrompt);
        if (mostRelevantPlugins.isEmpty()) {
            throw new AiException(UNABLE_TO_GENERATE_FLOW_ERROR);
        }

        return mostRelevantPlugins;
    }

    // Utility to minify a JSON schema by removing unnecessary fields
    private static void minifySchema(JsonNode node) {
        if (node == null) return;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            obj.remove("$dynamic");
            obj.remove("$group");
            if (obj.optional("default").map(d -> d.isBoolean() && !d.asBoolean()).orElse(false)) {
                obj.remove("default");
            }
            obj.properties().forEach(e -> minifySchema(e.getValue()));
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            for (JsonNode item : arr) {
                minifySchema(item);
            }
        }
    }

    private String generateFlowYaml(FlowYamlBuilder flowYamlBuilder, List<String> mostRelevantPluginTypes, String userPrompt) {
        JsonNode minifiedSchema = JacksonMapper.ofJson().convertValue(jsonSchemaGenerator.schemas(Flow.class, false, mostRelevantPluginTypes, true), JsonNode.class);
        minifySchema(minifiedSchema);
        String flowJsonSchemaString;
        try {
            flowJsonSchemaString = JacksonMapper.ofJson().writeValueAsString(minifiedSchema);
        } catch (JsonProcessingException e) {
            throw new AiException(UNABLE_TO_GENERATE_FLOW_ERROR);
        }

        String flowYaml = flowYamlBuilder.buildFlow(flowJsonSchemaString, NON_FLOW_REQUEST_ERROR, userPrompt);
        if (POSSIBLE_ERROR_MESSAGES.contains(flowYaml)) {
            throw new AiException(flowYaml);
        }

        flowYaml = flowYaml.replaceAll("\\s?```(?:yaml)?\\s?", "");

        return flowYaml;
    }

    public String generateFlow(PluginFinder pluginFinder, FlowYamlBuilder flowYamlBuilder, FlowGenerationPrompt flowGenerationPrompt) {
        String enhancedPrompt = """
            Current Flow YAML:
            ```yaml
            %s
            ```
            
            User's prompt:
            ```
            %s
            ```""".formatted(Optional.ofNullable(flowGenerationPrompt.flowYaml()).orElse(""), flowGenerationPrompt.userPrompt());

        List<String> mostRelevantPlugins = this.mostRelevantPlugins(pluginFinder, enhancedPrompt);

        String generatedFlowYaml = this.generateFlowYaml(flowYamlBuilder, mostRelevantPlugins, enhancedPrompt);
        if (generatedFlowYaml.equals(flowGenerationPrompt.flowYaml())) {
            throw new AiException(ALREADY_VALID_FLOW);
        }
        return generatedFlowYaml;
    }
}
