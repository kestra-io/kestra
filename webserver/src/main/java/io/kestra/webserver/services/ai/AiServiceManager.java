package io.kestra.webserver.services.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.gemini.GeminiAiService;
import io.kestra.webserver.services.ai.gemini.GeminiConfiguration;
import io.kestra.webserver.services.posthog.PosthogService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@Requires(property = "kestra.ai.enabled", value = "true", defaultValue = "true")
public class AiServiceManager {
    private final Map<String, AiServiceInterface> aiServices = new HashMap<>();
    private final AiProvidersConfiguration providersConfiguration;
    private String defaultProviderId;
    private boolean hasConfiguredProvider = false;
    protected final ExpressionContextService expressionContextService;
    protected final FlowParsingService flowParsingService;
    protected final NamespaceContextTool namespaceContextTool;
    protected final KestraDocsContextTool kestraDocsContextTool;

    public AiServiceManager(
        AiProvidersConfiguration providersConfiguration,
        Environment environment,
        // inject dependencies needed for AiService
        io.kestra.core.plugins.PluginRegistry pluginRegistry,
        io.kestra.core.docs.JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider,
        InstanceService instanceService,
        PosthogService posthogService,
        List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners,
        @Nullable NamespaceContextTool namespaceContextTool,
        @Nullable KestraDocsContextTool kestraDocsContextTool,
        ExpressionContextService expressionContextService,
        FlowParsingService flowParsingService) {
        this.providersConfiguration = providersConfiguration;
        this.expressionContextService = expressionContextService;
        this.flowParsingService = flowParsingService;
        this.namespaceContextTool = namespaceContextTool;
        this.kestraDocsContextTool = kestraDocsContextTool;

        List<AiProviderConfiguration> configs = new java.util.ArrayList<>(
            providersConfiguration.providers() != null ? providersConfiguration.providers() : List.of()
        );

        String legacyType = environment.get("kestra.ai.type", String.class).orElse(null);
        if (legacyType != null) {
            Map<String, Object> rawConfig = environment.get("kestra.ai." + legacyType, Map.class).orElse(null);

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

        // AI Copilot requires an explicitly configured provider. When none is configured no service is
        // registered, and the Copilot surfaces as unavailable until the user adds a provider.
        PropertyPlaceholderResolver placeholderResolver = environment.getPlaceholderResolver();
        for (AiProviderConfiguration rawProvider : configs) {
            AiProviderConfiguration provider = resolveConfigurationPlaceholders(rawProvider, placeholderResolver);
            AiServiceInterface aiService = createAiService(
                provider,
                pluginRegistry,
                jsonSchemaGenerator,
                versionProvider,
                instanceService,
                posthogService,
                listeners,
                expressionContextService,
                flowParsingService
            );
            if (aiService == null) {
                log.warn("AI service for provider '{}' could not be created, skipping.", provider.id());
                continue;
            }
            if (provider.isDefault()) {
                defaultProviderId = provider.id();
            }
            aiServices.put(provider.id(), aiService);
            hasConfiguredProvider = true;
        }
    }

    protected AiServiceInterface createAiService(
        AiProviderConfiguration provider,
        io.kestra.core.plugins.PluginRegistry pluginRegistry,
        io.kestra.core.docs.JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider,
        InstanceService instanceService,
        PosthogService posthogService,
        List<dev.langchain4j.model.chat.listener.ChatModelListener> listeners,
        ExpressionContextService expressionContextService,
        FlowParsingService flowParsingService) {
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
            AiService<?> service = new GeminiAiService(
                pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, this.namespaceContextTool, provider.displayName(), listeners, geminiConfig,
                expressionContextService, flowParsingService
            );
            if (this.kestraDocsContextTool != null) {
                service.setKestraDocsContextTool(this.kestraDocsContextTool);
            }
            return service;
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

    public boolean hasConfiguredProvider() {
        return hasConfiguredProvider;
    }

    /**
     * Returns a copy of the given provider whose configuration values have had their {@code ${...}} placeholders
     * resolved. Needed because Micronaut does not resolve placeholders for values nested inside an untyped
     * {@code Map<String, Object>} within a {@code List} element when bound from a YAML property source.
     */
    private static AiProviderConfiguration resolveConfigurationPlaceholders(AiProviderConfiguration provider, PropertyPlaceholderResolver resolver) {
        if (provider.configuration() == null) {
            return provider;
        }

        Map<String, Object> resolved = new HashMap<>(provider.configuration().size());
        provider.configuration().forEach((key, value) -> resolved.put(key, resolvePlaceholders(value, resolver)));

        return new AiProviderConfiguration(
            provider.id(),
            provider.displayName(),
            provider.type(),
            provider.isDefault(),
            resolved
        );
    }

    /**
     * Recursively resolves {@code ${...}} placeholders in {@code String} values found within maps and lists.
     * When a placeholder cannot be resolved, the original value is kept and a warning is logged so the AI service
     * initialization is not aborted by a single misconfigured value.
     */
    private static Object resolvePlaceholders(Object value, PropertyPlaceholderResolver resolver) {
        return switch (value) {
            case String string -> {
                try {
                    yield resolver.resolveRequiredPlaceholders(string);
                } catch (Exception e) {
                    log.warn("Could not resolve placeholder(s) in AI provider configuration value '{}': {}", string, e.getMessage());
                    yield string;
                }
            }
            case Map<?, ?> map -> {
                Map<Object, Object> resolved = new HashMap<>(map.size());
                map.forEach((k, v) -> resolved.put(k, resolvePlaceholders(v, resolver)));
                yield resolved;
            }
            case List<?> list -> list.stream().map(item -> resolvePlaceholders(item, resolver)).toList();
            case null, default -> value;
        };
    }
}
