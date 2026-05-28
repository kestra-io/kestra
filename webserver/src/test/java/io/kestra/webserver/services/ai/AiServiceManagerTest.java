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

import io.micronaut.core.value.PropertyResolver;
import io.micronaut.http.client.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiServiceManagerTest {

    @Mock
    HttpClient apiHttpClient;
    @Mock
    io.micronaut.http.client.BlockingHttpClient blockingHttpClient;
    @Mock
    AiProvidersConfiguration providersConfiguration;
    @Mock
    PropertyResolver propertyResolver;
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
    io.kestra.core.services.PluginDefaultService pluginDefaultService;

    private AiServiceManager buildManager(List<AiProviderConfiguration> providers) {
        when(providersConfiguration.providers()).thenReturn(providers);

        return new AiServiceManager(
            apiHttpClient,
            providersConfiguration,
            propertyResolver,
            pluginRegistry,
            jsonSchemaGenerator,
            versionProvider,
            instanceService,
            posthogService,
            List.of(),
            namespaceContextTool,
            kestraDocsContextTool,
            expressionContextService,
            pluginDefaultService
        );
    }

    @Test
    void hasConfiguredProviderShouldBeFalseWhenNoProvidersConfigured() {
        when(apiHttpClient.toBlocking()).thenReturn(blockingHttpClient);

        AiServiceManager manager = buildManager(null);

        assertThat(manager.hasConfiguredProvider()).isFalse();
    }

    @Test
    void hasConfiguredProviderShouldBeFalseWhenProviderListEmpty() {
        when(apiHttpClient.toBlocking()).thenReturn(blockingHttpClient);

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
}
