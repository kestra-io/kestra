package io.kestra.webserver.services.ai.azure.openai;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.*;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.ai.ThinkingEffort;
import io.kestra.webserver.services.posthog.PosthogService;

import com.azure.ai.openai.models.ReasoningEffortValue;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AzureOpenAiAiService extends AiService<AzureOpenAiConfiguration> {
    public static final String TYPE = "azure-openai";

    public AzureOpenAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, AzureOpenAiConfiguration azureOpenAiConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, azureOpenAiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().endpoint();
    }

    /**
     * Azure reasoning-effort value when thinking is on, else null; {@code none} is sent even with thinking off.
     * Azure OpenAI reasoning is stateless per request, so only the effort knob is needed — no cross-turn persistence.
     */
    private ReasoningEffortValue reasoningEffort() {
        ThinkingEffort effort = getAiConfiguration().thinkingEffort();
        if (effort == null || (!getAiConfiguration().thinkingEnabled() && ThinkingEffort.NONE != effort)) {
            return null;
        }
        return ReasoningEffortValue.fromString(effort.value());
    }

    private TokenCredential credentials(String tenantId, String clientId, String clientSecret) {

        if (StringUtils.isNotBlank(clientSecret)) {
            return new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .tenantId(tenantId)
                .clientSecret(clientSecret)
                .build();
        }

        return new DefaultAzureCredentialBuilder().tenantId(tenantId).build();
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        AzureOpenAiChatModel.Builder commonBuilder = AzureOpenAiChatModel.builder()
            .deploymentName(getAiConfiguration().modelName())
            .endpoint(getAiConfiguration().endpoint())
            .serviceVersion(getAiConfiguration().serviceVersion())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequestsAndResponses(getAiConfiguration().logRequestsAndResponses())
            .reasoningEffort(reasoningEffort())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout())
            .listeners(listeners);

        if (getAiConfiguration().apiKey() != null) {
            return commonBuilder
                .apiKey(getAiConfiguration().apiKey())
                .build();
        } else {
            return commonBuilder
                .tokenCredential(credentials(getAiConfiguration().tenantId(), getAiConfiguration().clientId(), getAiConfiguration().clientSecret()))
                .build();
        }
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        AzureOpenAiStreamingChatModel.Builder commonBuilder = AzureOpenAiStreamingChatModel.builder()
            .deploymentName(getAiConfiguration().modelName())
            .endpoint(getAiConfiguration().endpoint())
            .serviceVersion(getAiConfiguration().serviceVersion())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequestsAndResponses(getAiConfiguration().logRequestsAndResponses())
            .reasoningEffort(reasoningEffort())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout())
            .listeners(listeners);

        if (getAiConfiguration().apiKey() != null) {
            return commonBuilder
                .apiKey(getAiConfiguration().apiKey())
                .build();
        } else {
            return commonBuilder
                .tokenCredential(credentials(getAiConfiguration().tenantId(), getAiConfiguration().clientId(), getAiConfiguration().clientSecret()))
                .build();
        }
    }
}
