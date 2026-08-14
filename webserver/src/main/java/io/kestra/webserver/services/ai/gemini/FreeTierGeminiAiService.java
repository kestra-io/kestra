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
 * <p>Identical to {@link GeminiAiService} in every respect that matters to the agent — same prompts, same
 * tools, same streaming, same thought-signature round-trip — because it <em>is</em> that service, with a
 * different base URL and an added identity header. That is deliberate: a separate implementation would be a
 * way for the free tier to drift from the paid one.
 *
 * <p>The identity header is what lets the relay meter spend.
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
     * The relay's budget, and nothing else's.
     *
     * <p>Alone among the providers, this one's ceiling is not the operator's to set: the allowance belongs to
     * Kestra and is enforced at the relay, so the figure shown here has to be the one that will refuse the turn.
     * {@link AiFreeTierLimitProvider#limit()} is the single source of it, and is also what lets an operator cap
     * their own spend lower than the allowance.
     *
     * <p>Deliberately does not fall back to {@code super.usageLimit()}. The configuration this service is built
     * from is synthesized by {@code AiServiceManager} and carries no ceiling, so consulting it would read as a
     * second source while returning nothing — and one caller reading a stale second source is exactly how the
     * ceiling enforced mid-turn stops matching the one the client was shown.
     */
    @Override
    public Optional<AiUsageLimitConfiguration> usageLimit() {
        return limitProvider != null ? limitProvider.limit() : Optional.empty();
    }

    /**
     * Identity for a turn: the instance always, the user when the caller has one.
     *
     * <p>The user id travels on the principal rather than being looked up here, and that is the whole point.
     * This supplier runs while the client library builds each request, on whichever thread the agent turn is
     * executing — so a request-scoped lookup such as {@code CurrentUserContext} would come back empty and
     * quietly degrade to instance-only metering while appearing to work. The principal is resolved on the
     * request thread by the controller and carried through the turn, so reading it here is safe.
     *
     * <p>In OSS the principal is always {@code null} ({@code DefaultAgentPrincipalResolver}), so only the
     * instance is sent and the relay meters the instance — which is what it does when no user is named.
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
