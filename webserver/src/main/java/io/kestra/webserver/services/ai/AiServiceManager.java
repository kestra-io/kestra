package io.kestra.webserver.services.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.api.ApiAiService;
import io.kestra.webserver.services.ai.gemini.GeminiAiService;
import io.kestra.webserver.services.ai.gemini.GeminiConfiguration;
import io.kestra.webserver.services.posthog.PosthogService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@Requires(property = "kestra.ai.enabled", value = "true", defaultValue = "true")
public class AiServiceManager {
    private final Map<String, AiServiceInterface> aiServices = new HashMap<>();
    private final AiProvidersConfiguration providersConfiguration;
    private String defaultProviderId;
    protected final NamespaceContextTool namespaceContextTool;

    public AiServiceManager(
        @Client("api") HttpClient apiHttpClient,
        AiProvidersConfiguration providersConfiguration,
        PropertyResolver propertyResolver,
        // inject dependencies needed for AiService
        io.kestra.core.plugins.PluginRegistry pluginRegistry,
        io.kestra.core.docs.JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider,
        InstanceService instanceService,
        PosthogService posthogService,
        List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners,
        NamespaceContextTool namespaceContextTool) {
        this.providersConfiguration = providersConfiguration;
        this.namespaceContextTool = namespaceContextTool;

        List<AiProviderConfiguration> configs = new java.util.ArrayList<>(
            providersConfiguration.providers() != null ? providersConfiguration.providers() : List.of()
        );

        String legacyType = propertyResolver.get("kestra.ai.type", String.class).orElse(null);
        if (legacyType != null) {
            Map<String, Object> rawConfig = propertyResolver.get("kestra.ai." + legacyType, Map.class).orElse(null);

            Map<String, Object> legacyConfig = rawConfig.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(e -> io.micronaut.core.naming.NameUtils.camelCase(e.getKey()), Map.Entry::getValue));

            configs.add(
                new AiProviderConfiguration(
                    legacyType + "-legacy",
                    legacyType.toUpperCase(),
                    legacyType,
                    false,
                    legacyConfig
                )
            );
        }

        if (!configs.isEmpty()) {
            for (AiProviderConfiguration provider : configs) {
                AiServiceInterface aiService = createAiService(
                    provider,
                    pluginRegistry,
                    jsonSchemaGenerator,
                    versionProvider,
                    instanceService,
                    posthogService,
                    listeners
                );
                if (aiService == null) {
                    log.warn("AI service for provider '{}' could not be created, skipping.", provider.id());
                    continue;
                }
                if (provider.isDefault()) {
                    defaultProviderId = provider.id();
                }
                aiServices.put(provider.id(), aiService);
            }
        } else {
            defaultProviderId = "api";
            aiServices.put(defaultProviderId, new ApiAiService(apiHttpClient.toBlocking(), instanceService));
        }
    }

    protected AiServiceInterface createAiService(
        AiProviderConfiguration provider,
        io.kestra.core.plugins.PluginRegistry pluginRegistry,
        io.kestra.core.docs.JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider,
        InstanceService instanceService,
        PosthogService posthogService,
        List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners) {
        String type = provider.type();
        Map<String, Object> configMap = provider.configuration();
        if (configMap == null) {
            log.warn("Configuration is null for provider {}", provider.id());
            return null;
        }

        if (!"gemini".equals(type)) {
            throw new IllegalArgumentException(
                "Unsupported AI provider type '" + type + "' for Kestra OSS. Only 'gemini' is supported. " +
                "Other providers (openai, anthropic, ollama, etc.) require Kestra Enterprise Edition."
            );
        }

        try {
            ObjectMapper mapper = JacksonMapper.ofJson().copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            GeminiConfiguration geminiConfig = mapper.convertValue(configMap, GeminiConfiguration.class);
            return new GeminiAiService(
                pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, provider.displayName(), listeners, geminiConfig
            );
        } catch (Exception e) {
            log.error("Failed to create AI service for provider {}: {}", provider.id(), e.getMessage());
            return null;
        }
    }

    public AiServiceInterface getAiService(String id) {
        if (id == null) {
            return getDefaultAiService();
        }
        return aiServices.get(id);
    }

    public Map<String, AiServiceInterface> getAllAiServices() {
        return aiServices;
    }

    public AiServiceInterface getDefaultAiService() {
        if (providersConfiguration.providers() != null) {
            for (AiProviderConfiguration provider : providersConfiguration.providers()) {
                if (provider.isDefault()) {
                    return aiServices.get(provider.id());
                }
            }
        }
        return aiServices.values().stream().findFirst().orElse(null);
    }

    public String getDefaultProviderId() {
        return defaultProviderId;
    }
}
