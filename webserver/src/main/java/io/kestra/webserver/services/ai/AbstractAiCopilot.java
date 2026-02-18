package io.kestra.webserver.services.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.exceptions.AiException;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Version;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAiCopilot {
    protected final JsonSchemaGenerator jsonSchemaGenerator;
    protected final PluginRegistry pluginRegistry;
    protected final String fallbackPluginVersion;

    protected List<String> mostRelevantPlugins(PluginFinder pluginFinder, String userPrompt, List<String> excludedPluginTypes) {
        Map<String, String> descriptionByType = pluginRegistry.plugins().stream()
            .sorted(Comparator.comparing(p -> Version.of(Optional.ofNullable(p.version()).orElse(fallbackPluginVersion))))
            .flatMap(plugin -> plugin.allClassGrouped().entrySet().stream().filter(e -> !excludedPluginTypes.contains(e.getKey())).map(Map.Entry::getValue).flatMap(Collection::stream))
            .map(clazz -> Map.entry(clazz.getName(), Optional.ofNullable(((Class<?>) clazz).getDeclaredAnnotation(io.swagger.v3.oas.annotations.media.Schema.class))))
            .filter(e -> !e.getValue().map(io.swagger.v3.oas.annotations.media.Schema::deprecated).orElse(false))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue()
                        .map(io.swagger.v3.oas.annotations.media.Schema::title)
                        .orElse(""),
                    (existing, replacement) -> existing
                )
            );

        String serializedPlugins;
        try {
            serializedPlugins = JacksonMapper.ofJson().writeValueAsString(descriptionByType.entrySet().stream().map(e ->
                Map.of("type", e.getKey(), "description", e.getValue())
            ).toList());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize plugin types for AI agent", e);
            serializedPlugins = "[]";
        }

        var mostRelevantPlugins = pluginFinder.findPlugins(serializedPlugins, userPrompt);
        if (mostRelevantPlugins.isEmpty()) {
            throw new AiException(this.unableToGenerateMessage());
        }

        return mostRelevantPlugins;
    }

    protected static void minifySchema(JsonNode node) {
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

    @FunctionalInterface
    protected interface YamlBuilderFn {
        String build(String schemaJson, String generationError, String userPrompt);
    }

    protected String generateYaml(
        YamlBuilderFn builderFn,
        Class<?> modelClass,
        List<String> mostRelevantPluginTypes,
        String generationError,
        List<String> possibleErrorMessages,
        String userPrompt,
        String originalYaml,
        String alreadyValidMessage
    ) {
        JsonNode minifiedSchema = JacksonMapper.ofJson().convertValue(jsonSchemaGenerator.schemas(modelClass, false, mostRelevantPluginTypes, true), JsonNode.class);
        minifySchema(minifiedSchema);
        String jsonSchemaString;
        try {
            jsonSchemaString = JacksonMapper.ofJson().writeValueAsString(minifiedSchema);
        } catch (JsonProcessingException e) {
            throw new AiException(generationError);
        }

        String yaml = builderFn.build(jsonSchemaString, generationError, userPrompt);
        if (possibleErrorMessages != null && possibleErrorMessages.contains(yaml)) {
            throw new AiException(yaml);
        }

        yaml = yaml.replaceAll("\\s?```(?:yaml)?\\s?", "");

        if (originalYaml != null && yaml.equals(originalYaml)) {
            throw new AiException(alreadyValidMessage);
        }

        return yaml;
    }

    protected abstract String alreadyValidMessage();
    protected abstract String nonRequestMessage();
    protected abstract String unableToGenerateMessage();
    protected abstract List<String> possibleErrorMessages();
    protected abstract List<String> excludedPluginTypes();
}

