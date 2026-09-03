package io.kestra.webserver.services.ai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.posthog.PosthogService;

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertyPlaceholderResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceManagerTest {

    @Mock
    AiProvidersConfiguration providersConfiguration;
    @Mock
    Environment environment;
    @Mock
    PluginRegistry pluginRegistry;
    @Mock
    JsonSchemaGenerator jsonSchemaGenerator;
    @Mock
    VersionProvider versionProvider;
    @Mock
    InstanceService instanceService;
    @Mock
    PosthogService posthogService;
    @Mock
    NamespaceContextTool namespaceContextTool;
    @Mock
    KestraDocsContextTool kestraDocsContextTool;
    @Mock
    ExpressionContextService expressionContextService;
    @Mock
    io.kestra.core.services.FlowParsingService flowParsingService;

    private final PropertyPlaceholderResolver placeholderResolver = mock(PropertyPlaceholderResolver.class);

    private AiServiceManager buildManager(List<AiProviderConfiguration> providers) {
        when(providersConfiguration.providers()).thenReturn(providers);
        // Only exercised when at least one provider is configured, hence lenient.
        lenient().when(environment.getPlaceholderResolver()).thenReturn(placeholderResolver);
        lenient().when(placeholderResolver.resolveRequiredPlaceholders(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        return new AiServiceManager(
            providersConfiguration,
            environment,
            pluginRegistry,
            jsonSchemaGenerator,
            versionProvider,
            instanceService,
            posthogService,
            List.of(),
            namespaceContextTool,
            kestraDocsContextTool,
            expressionContextService,
            flowParsingService
        );
    }

    @Test
    void hasConfiguredProviderShouldBeFalseWhenNoProvidersConfigured() {
        AiServiceManager manager = buildManager(null);

        assertThat(manager.hasConfiguredProvider()).isFalse();
    }

    @Test
    void hasConfiguredProviderShouldBeFalseWhenProviderListEmpty() {
        AiServiceManager manager = buildManager(List.of());

        assertThat(manager.hasConfiguredProvider()).isFalse();
    }

    @Test
    void hasConfiguredProviderShouldBeTrueWhenGeminiProviderConfigured() {
        AiProviderConfiguration geminiProvider = new AiProviderConfiguration(
            "gemini-test",
            "Gemini",
            "gemini",
            true,
            java.util.Map.of("modelName", "gemini-2.5-flash", "apiKey", "fake-key")
        );

        AiServiceManager manager = buildManager(List.of(geminiProvider));

        assertThat(manager.hasConfiguredProvider()).isTrue();
    }

    @Test
    void shouldResolvePlaceholdersInProviderConfiguration() {
        AiProviderConfiguration geminiProvider = new AiProviderConfiguration(
            "gemini-test",
            "Gemini",
            "gemini",
            true,
            java.util.Map.of("modelName", "gemini-2.5-flash", "apiKey", "${GEMINI_API_KEY}")
        );

        buildManager(List.of(geminiProvider));

        // The ${...} value must be sent through the placeholder resolver rather than passed verbatim.
        verify(placeholderResolver).resolveRequiredPlaceholders("${GEMINI_API_KEY}");
    }
}
