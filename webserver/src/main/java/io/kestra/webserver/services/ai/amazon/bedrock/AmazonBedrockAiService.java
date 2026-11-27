package io.kestra.webserver.services.ai.amazon.bedrock;

import java.util.List;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.bedrock.BedrockStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Slf4j
public class AmazonBedrockAiService extends AiService<AmazonBedrockConfiguration> {
    public static final String TYPE = "bedrock";

    /** Bedrock (Anthropic) requires the reasoning budget to be at least 1024 tokens; used when thinking is enabled with no explicit budget. */
    private static final int DEFAULT_THINKING_BUDGET_TOKENS = 1024;

    public AmazonBedrockAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, AmazonBedrockConfiguration amazonBedrockConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, amazonBedrockConfiguration,
            expressionContextService, flowParsingService
        );
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        var credentials = AwsBasicCredentials.create(
            getAiConfiguration().accessKeyId(),
            getAiConfiguration().secretAccessKey()
        );
        var credentialsProvider = StaticCredentialsProvider.create(credentials);
        boolean thinking = getAiConfiguration().thinkingEnabled();

        return BedrockChatModel.builder()
            .listeners(listeners)
            .client(
                BedrockRuntimeClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .build()
            )
            .modelId(getAiConfiguration().modelName())
            .defaultRequestParameters(requestParameters(thinking))
            .sendThinking(thinking)
            .returnThinking(thinking)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout())
            .build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        var credentials = AwsBasicCredentials.create(
            getAiConfiguration().accessKeyId(),
            getAiConfiguration().secretAccessKey()
        );
        var credentialsProvider = StaticCredentialsProvider.create(credentials);
        boolean thinking = getAiConfiguration().thinkingEnabled();

        return BedrockStreamingChatModel.builder()
            .listeners(listeners)
            .client(
                BedrockRuntimeAsyncClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .build()
            )
            .modelId(getAiConfiguration().modelName())
            .defaultRequestParameters(requestParameters(thinking))
            .sendThinking(thinking)
            .returnThinking(thinking)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout())
            .build();
    }

    /**
     * Builds the request parameters, enabling reasoning with a token budget when thinking is on. Bedrock's
     * Anthropic models reject topP/topK and a temperature other than 1 while reasoning, so those are dropped
     * in that mode.
     */
    private BedrockChatRequestParameters requestParameters(final boolean thinking) {
        BedrockChatRequestParameters.Builder builder = BedrockChatRequestParameters.builder()
            .maxOutputTokens(getAiConfiguration().maxOutputTokens())
            .temperature(thinking ? 1.0 : getAiConfiguration().temperature());
        if (thinking) {
            builder.enableReasoning(thinkingBudgetTokens());
        } else {
            builder.topP(getAiConfiguration().topP()).topK(getAiConfiguration().topK());
        }
        return builder.build();
    }

    /** The configured thinking budget, or the Bedrock minimum when thinking is enabled without an explicit budget. */
    private int thinkingBudgetTokens() {
        Integer configured = getAiConfiguration().thinkingBudgetTokens();
        return configured != null ? configured : DEFAULT_THINKING_BUDGET_TOKENS;
    }
}
