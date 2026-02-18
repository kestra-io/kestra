package io.kestra.webserver.services.ai;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static io.kestra.core.plugins.RegisteredPlugin.*;

@Slf4j
public class FlowAiCopilot extends AbstractAiCopilot {
    private static final String ALREADY_VALID_MESSAGE = "This flow already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    private static final String NON_REQUEST_ERROR = "I can only assist with creating Kestra flows.";
    private static final String UNABLE_TO_GENERATE_ERROR = "The prompt did not provide enough information to generate a valid flow. Please clarify your request.";
    private static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_MESSAGE, NON_REQUEST_ERROR, UNABLE_TO_GENERATE_ERROR);

    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME,
        SECRETS_GROUP_NAME,
        APPS_GROUP_NAME,
        APP_BLOCKS_GROUP_NAME,
        CHARTS_GROUP_NAME,
        DATA_FILTERS_GROUP_NAME,
        DATA_FILTERS_KPI_GROUP_NAME
    );

    public FlowAiCopilot(JsonSchemaGenerator jsonSchemaGenerator, PluginRegistry pluginRegistry, String fallbackPluginVersion) {
        super(jsonSchemaGenerator, pluginRegistry, fallbackPluginVersion);
    }

    @Override
    protected String alreadyValidMessage() {
        return ALREADY_VALID_MESSAGE;
    }

    @Override
    protected String nonRequestMessage() {
        return NON_REQUEST_ERROR;
    }

    @Override
    protected String unableToGenerateMessage() {
        return UNABLE_TO_GENERATE_ERROR;
    }

    @Override
    protected List<String> possibleErrorMessages() {
        return POSSIBLE_ERROR_MESSAGES;
    }

    @Override
    protected List<String> excludedPluginTypes() {
        return EXCLUDED_PLUGIN_TYPES;
    }

    public String generateFlow(PluginFinder pluginFinder, FlowYamlBuilder flowYamlBuilder, FlowGenerationPrompt flowGenerationPrompt) {
        String enhancedPrompt = String.format("Current Flow YAML:\n```yaml\n%s\n```\n\nUser's prompt:\n``\n%s\n```", java.util.Optional.ofNullable(flowGenerationPrompt.yaml()).orElse(""), flowGenerationPrompt.userPrompt());

        List<String> mostRelevantPlugins = this.mostRelevantPlugins(pluginFinder, enhancedPrompt, this.excludedPluginTypes());

        return this.generateYaml(
            flowYamlBuilder::buildFlow,
            Flow.class,
            mostRelevantPlugins,
            NON_REQUEST_ERROR,
            POSSIBLE_ERROR_MESSAGES,
            enhancedPrompt,
            flowGenerationPrompt.yaml(),
            ALREADY_VALID_MESSAGE
        );
    }
}
