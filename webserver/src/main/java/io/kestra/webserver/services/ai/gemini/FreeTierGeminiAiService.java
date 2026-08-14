package io.kestra.webserver.services.ai.gemini;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiFreeTierLimitProvider;
import io.kestra.webserver.services.ai.AiUsageLimitConfiguration;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.posthog.PosthogService;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Gemini provider pointed at Kestra's hosted relay instead of Google directly.
 *
 * <p>Extends {@link GeminiAiService} rather than reimplementing it, so the free tier cannot drift from the
 * configured one: same prompts, tools, streaming and thought-signature round-trip, with a different base URL
 * and an identity header the relay meters spend against.
 */
public class FreeTierGeminiAiService extends GeminiAiService {
    private static final String INSTANCE_ID_HEADER = "X-Kestra-Instance-Id";
    private static final String USER_ID_HEADER = "X-Kestra-User-Id";

    private final InstanceService instanceService;
    private final AiFreeTierLimitProvider limitProvider;

    public FreeTierGeminiAiService(
        PluginRegistry pluginRegistry,
        JsonSchemaGenerator jsonSchemaGenerator,
        VersionProvider versionProvider,
        InstanceService instanceService,
        PosthogService posthogService,
        @Nullable NamespaceContextTool namespaceContextTool,
        String displayName,
        List<ChatModelListener> listeners,
        GeminiConfiguration geminiConfiguration,
        ExpressionContextService expressionContextService,
        FlowParsingService flowParsingService,
        @Nullable AiFreeTierLimitProvider limitProvider
    ) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool,
            displayName, listeners, geminiConfiguration, expressionContextService, flowParsingService
        );
        this.instanceService = instanceService;
        this.limitProvider = limitProvider;
    }

    /**
     * The relay's budget, and nothing else's — this provider's ceiling is set and enforced at the relay, so
     * {@link AiFreeTierLimitProvider#limit()} has to be its single source.
     *
     * <p>Deliberately no fallback to {@code super.usageLimit()}: the configuration this service is built from
     * is synthesized by {@code AiServiceManager} and carries no ceiling, so it would be a second source
     * returning nothing.
     */
    @Override
    public Optional<AiUsageLimitConfiguration> usageLimit() {
        return limitProvider != null ? limitProvider.limit() : Optional.empty();
    }

    /**
     * Identity for a turn: the instance always, the user when the caller has one.
     *
     * <p>The user id has to travel on the principal. This supplier runs while the client builds each request,
     * on whichever thread the turn is executing, so a request-scoped lookup would come back empty and degrade
     * to instance-only metering while appearing to work. Where no principal names a user, only the instance is
     * sent and the relay meters that.
     */
    @Override
    public StreamingChatModel streamingChatModel(@Nullable AgentPrincipal principal, List<ChatModelListener> listeners) {
        Map<String, String> identity = identityHeaders(principal);
        return buildStreamingChatModel(() -> identity, listeners);
    }

    /** The identity the relay meters against, exposed so the header contract can be asserted directly. */
    public Map<String, String> identityHeaders(@Nullable AgentPrincipal principal) {
        Map<String, String> headers = new HashMap<>();

        Map<String, String> fromConfiguration = super.customHeaders().get();
        if (fromConfiguration != null) {
            headers.putAll(fromConfiguration);
        }

        headers.put(INSTANCE_ID_HEADER, instanceService.fetch());

        String userId = principal == null ? null : principal.userId();
        if (userId != null && !userId.isBlank()) {
            headers.put(USER_ID_HEADER, userId);
        }

        return headers;
    }
}
