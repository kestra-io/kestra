package io.kestra.webserver.services.ai;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.webserver.models.ai.DashboardGenerationPrompt;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static io.kestra.core.plugins.RegisteredPlugin.*;

@Slf4j
public class DashboardAiCopilot extends AbstractAiCopilot {
    private static final String ALREADY_VALID_MESSAGE = "This dashboard already performs the requested action. Please provide additional instructions if you would like to request modifications.";
    private static final String NON_REQUEST_ERROR = "I can only assist with creating Kestra dashboards.";
    private static final String UNABLE_TO_GENERATE_ERROR = "The prompt did not provide enough information to generate a valid dashboard. Please clarify your request.";
    private static final List<String> POSSIBLE_ERROR_MESSAGES = List.of(ALREADY_VALID_MESSAGE, NON_REQUEST_ERROR, UNABLE_TO_GENERATE_ERROR);

    private static final List<String> EXCLUDED_PLUGIN_TYPES = List.of(
        STORAGES_GROUP_NAME,
        SECRETS_GROUP_NAME,
        APPS_GROUP_NAME,
        APP_BLOCKS_GROUP_NAME,
        TASKS_GROUP_NAME,
        TRIGGERS_GROUP_NAME,
        CONDITIONS_GROUP_NAME,
        ASSETS_GROUP_NAME,
        ASSETS_EXPORTERS_GROUP_NAME,
        LOG_EXPORTERS_GROUP_NAME
    );

    public DashboardAiCopilot(JsonSchemaGenerator jsonSchemaGenerator, PluginRegistry pluginRegistry, String fallbackPluginVersion) {
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

    public String generateDashboard(PluginFinder pluginFinder, DashboardYamlBuilder dashboardYamlBuilder, DashboardGenerationPrompt dashboardGenerationPrompt) {
        String enhancedPrompt = String.format("Current Dashboard YAML:\n```yaml\n%s\n```\n\nUser's prompt:\n``\n%s\n```", java.util.Optional.ofNullable(dashboardGenerationPrompt.yaml()).orElse(""), dashboardGenerationPrompt.userPrompt());

        java.util.List<String> mostRelevantPlugins = this.mostRelevantPlugins(pluginFinder, enhancedPrompt, this.excludedPluginTypes());

        return this.generateYaml(
            dashboardYamlBuilder::buildDashboard,
            Dashboard.class,
            mostRelevantPlugins,
            NON_REQUEST_ERROR,
            POSSIBLE_ERROR_MESSAGES,
            enhancedPrompt,
            dashboardGenerationPrompt.yaml(),
            ALREADY_VALID_MESSAGE
        );
    }
}
