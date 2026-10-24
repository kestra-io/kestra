package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.assertions.Problems;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.libs.copilot.exceptions.AiException;
import io.kestra.libs.copilot.services.ai.FlowYamlBuilder;
import io.kestra.libs.copilot.services.ai.PluginFinder;
import io.kestra.webserver.errors.ProblemTypes;
import io.kestra.webserver.services.ai.AiConfiguration;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.KestraDocsContextTool;
import io.kestra.webserver.services.ai.MetadataAppenderChatModelListener;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@KestraTest
@Property(name = "micronaut.server.host", value = "127.0.0.1")
@Property(name = "micronaut.server.port", value = "-1")
@Property(name = "kestra.anonymous-usage-report.enabled", value = "false")
class AiControllerTest {
    private static final String GENERATE_FLOW_URL = "/api/v1/main/ai/generate/flow";
    private static final String CONVERSATION_ID = "http-generation-test";
    private static final String PROVIDER_ID = "local-stub";
    private static final String FLOW_YAML = """
        id: generated
        namespace: io.kestra.tests
        tasks:
          - id: hello
            type: io.kestra.plugin.core.log.Log
            message: Hello
        """;

    private final PluginFinder pluginFinder = mock(PluginFinder.class);
    private final FlowYamlBuilder flowYamlBuilder = mock(FlowYamlBuilder.class);
    private TestAiService service;

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    AiServiceManager aiServiceManager;

    @Inject
    EmbeddedServer server;

    @MockBean(AiServiceManager.class)
    AiServiceManager aiServiceManager(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider, InstanceService instanceService, PosthogService posthogService,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        service = spy(
            new TestAiService(
                pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService,
                expressionContextService, flowParsingService, pluginFinder, flowYamlBuilder
            )
        );
        AiServiceManager manager = mock(AiServiceManager.class);
        when(manager.getAiService(PROVIDER_ID)).thenReturn(service);
        return manager;
    }

    @MockBean(PosthogService.class)
    PosthogService posthogService() {
        return mock(PosthogService.class);
    }

    @MockBean(KestraDocsContextTool.class)
    KestraDocsContextTool kestraDocsContextTool() {
        return mock(KestraDocsContextTool.class);
    }

    @MockBean(DocsMcpToolProvider.class)
    DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    @Test
    void shouldRecoverAndCleanUpWhenGenerationFailsBetweenSuccessfulRequests() {
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getURI().getHost()).isEqualTo("127.0.0.1");
        assertThat(server.getPort()).isPositive().isNotEqualTo(8080);
        MetadataAppenderChatModelListener listener = service.metadataListener();
        assertThat(listener.conversationMetadataGetter().get()).isNull();
        AiException failure = new AiException("Generation failed.");
        AtomicInteger generationCalls = new AtomicInteger();
        when(pluginFinder.findPlugins(anyString(), anyString())).thenReturn(List.of("io.kestra.plugin.core.log.Log"));
        when(flowYamlBuilder.buildFlow(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation ->
        {
            AiService.ConversationMetadata metadata = listener.conversationMetadataGetter().get();
            assertThat(metadata).isNotNull();
            assertThat(metadata.conversationId()).isEqualTo(CONVERSATION_ID);
            int attempt = generationCalls.incrementAndGet();
            if (attempt == 2) {
                throw failure;
            }
            assertThat(attempt).isIn(1, 3);
            return FLOW_YAML;
        });

        assertSuccessfulResponse(generateFlow());
        assertLifecycleCounts(listener, 1, 1, 0);

        HttpClientResponseException exception = catchThrowableOfType(HttpClientResponseException.class, this::generateFlow);

        assertThat(exception).isNotNull();
        var problem = Problems.assertProblem(exception, ProblemTypes.AI_REQUEST_FAILED);
        assertThat(problem.detail()).isEqualTo(failure.getMessage());
        assertThat(problem.instance()).isEqualTo(GENERATE_FLOW_URL);
        assertLifecycleCounts(listener, 2, 1, 1);
        ArgumentCaptor<Throwable> failureCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(service).onGenerationFailure(any(), failureCaptor.capture());
        assertThat(failureCaptor.getValue()).isSameAs(failure).hasNoSuppressedExceptions();

        assertSuccessfulResponse(generateFlow());
        assertLifecycleCounts(listener, 3, 2, 1);
        assertThat(generationCalls.get()).isEqualTo(3);
        verify(aiServiceManager, times(3)).getAiService(PROVIDER_ID);
        verify(service, times(3)).generateFlow(any(), any(), eq(TenantService.MAIN_TENANT));
        verify(service, never()).chatModel(any());
    }

    @Test
    void shouldReturn503WhenProviderNotFound() {
        HttpClientResponseException exception = catchThrowableOfType(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                HttpRequest.POST(
                    GENERATE_FLOW_URL,
                    new AiController.FlowGenerationPrompt(IdUtils.create(), "Generate a flow", "yaml", "io.kestra.tests", "nonexistent-provider")
                ),
                String.class
            )
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getStatus().getCode()).isEqualTo(503);
    }

    private HttpResponse<String> generateFlow() {
        return client.toBlocking().exchange(
            HttpRequest.POST(
                GENERATE_FLOW_URL,
                new AiController.FlowGenerationPrompt(CONVERSATION_ID, "Generate a flow", "", "io.kestra.tests", PROVIDER_ID)
            ).accept("application/yaml", "application/problem+json"),
            String.class
        );
    }

    private void assertSuccessfulResponse(HttpResponse<String> response) {
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getContentType()).hasValue(MediaType.of("application/yaml"));
        assertThat(response.body()).isEqualTo(FLOW_YAML);
    }

    private void assertLifecycleCounts(MetadataAppenderChatModelListener listener, int attempts, int successes, int failures) {
        assertThat(listener.conversationMetadataGetter().get()).isNull();
        verify(service, times(attempts)).beforeGeneration(any(), eq(CONVERSATION_ID), anyString(), anyMap());
        verify(service, times(successes)).afterGeneration(any(), any(), any(), any(), any());
        verify(service, times(failures)).onGenerationFailure(any(), any());
    }

    private static class TestAiService extends AiService<AiConfiguration> {
        private final PluginFinder pluginFinder;
        private final FlowYamlBuilder flowYamlBuilder;

        private TestAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider,
            InstanceService instanceService, PosthogService posthogService, ExpressionContextService expressionContextService,
            FlowParsingService flowParsingService, PluginFinder pluginFinder, FlowYamlBuilder flowYamlBuilder) {
            super(
                pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, null,
                PROVIDER_ID, "Test", List.of(), new TestAiConfiguration(PROVIDER_ID, "stub"), expressionContextService, flowParsingService
            );
            this.pluginFinder = pluginFinder;
            this.flowYamlBuilder = flowYamlBuilder;
        }

        @Override
        public ChatModel chatModel(List<ChatModelListener> listeners) {
            throw new AssertionError("HTTP tests must not create a provider chat model.");
        }

        @Override
        protected PluginFinder pluginFinder(String conversationId) {
            return pluginFinder;
        }

        @Override
        protected FlowYamlBuilder flowYamlBuilder(String conversationId) {
            return flowYamlBuilder;
        }

        private MetadataAppenderChatModelListener metadataListener() {
            return (MetadataAppenderChatModelListener) listeners("Test", CONVERSATION_ID).getLast();
        }
    }

    private record TestAiConfiguration(String type, String modelName) implements AiConfiguration {
    }
}
