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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
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
        int declaredProviderCount = configs.size();

        String legacyType = environment.get("kestra.ai.type", String.class).orElse(null);
        if (legacyType != null) {
            Map<String, Object> rawConfig = environment.get("kestra.ai." + legacyType, Map.class).orElse(null);

            Map<String, Object> legacyConfig = normalizeConfigurationKeys(rawConfig);

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
        for (int i = 0; i < configs.size(); i++) {
            // The legacy single-provider configuration, when set, is the entry appended past the declared ones.
            String configurationPath = i < declaredProviderCount ? "kestra.ai.providers[" + i + "].configuration" : "kestra.ai." + legacyType;
            AiProviderConfiguration provider = resolveConfiguration(configs.get(i), configurationPath, placeholderResolver, environment);
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
     * Returns a copy of the given provider ready for Jackson: its custom HTTP headers re-read from the property
     * source, and its {@code ${...}} placeholders resolved. Both are needed because Micronaut applies its
     * camel-case key convention at every nesting level of the untyped configuration map — which would turn the
     * header name {@code X-Api-Key} into {@code xApiKey} — and resolves no placeholder nested inside it.
     */
    private static AiProviderConfiguration resolveConfiguration(
        AiProviderConfiguration provider,
        String configurationPath,
        PropertyPlaceholderResolver resolver,
        Environment environment) {
        if (provider.configuration() == null) {
            return provider;
        }

        Map<String, Object> configuration = new HashMap<>(provider.configuration());
        Map<String, Object> customHeaders = rawCustomHeaders(configurationPath, environment);
        if (!customHeaders.isEmpty()) {
            configuration.put("customHeaders", customHeaders);
        }
        configuration.replaceAll((key, value) -> resolvePlaceholders(value, resolver));

        return new AiProviderConfiguration(
            provider.id(),
            provider.displayName(),
            provider.type(),
            provider.isDefault(),
            configuration,
            provider.systemPrompt()
        );
    }

    /** Returns the provider's custom headers with the names exactly as written, empty when it declares none. */
    private static Map<String, Object> rawCustomHeaders(String configurationPath, Environment environment) {
        try {
            Map<String, Object> headers = environment.getProperties(configurationPath + ".custom-headers", StringConvention.RAW);
            // The property may also be written in camel case, which lands under a different raw key.
            return headers.isEmpty() ? environment.getProperties(configurationPath + ".customHeaders", StringConvention.RAW) : headers;
        } catch (Exception e) {
            // Reading raw properties resolves placeholders, which throws when one cannot be resolved: keep the
            // bound headers rather than aborting the startup over a single misconfigured value.
            log.warn("Could not read the custom headers of the AI provider at '{}': {}", configurationPath, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Camel-cases the provider properties so Jackson binds them to the typed configuration. Only the legacy
     * single-provider configuration needs it, being read as a raw map: Micronaut already camel-cases the keys of a
     * {@code providers} entry. Nested maps are left alone — {@code customHeaders} is keyed by HTTP header names.
     */
    static Map<String, Object> normalizeConfigurationKeys(Map<String, Object> configuration) {
        Map<String, Object> normalized = new HashMap<>(configuration.size());
        configuration.forEach((key, value) -> normalized.put(NameUtils.camelCase(key), value));
        return normalized;
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
