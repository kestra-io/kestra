package io.kestra.webserver.services.ai.agent;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;

import io.kestra.webserver.services.ai.AiServiceInterface;
import io.kestra.webserver.services.ai.AiServiceManager;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import io.micronaut.test.annotation.MockBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared scaffolding for any test that exercises the Copilot agent: a configured AI provider backed by a
 * {@link ScriptedStreamingChatModel} the test scripts, and a stubbed docs MCP provider so no test reaches
 * the real endpoint.
 * <p>
 * Deliberately carries no {@code @KestraTest}: subclasses declare their own so each can set the
 * {@code @Property} overrides and environments its scenario needs. Micronaut resolves inherited
 * {@code @MockBean} factory methods, so the mocks below apply to every subclass — but it invokes the
 * declaring method rather than dispatching virtually, so <em>overriding</em> one of these factories in a
 * subclass has no effect. A test that needs a different mock (for instance
 * {@code AiAgentControllerNoProviderTest}, which needs no configured provider at all) must declare its own
 * and not extend this class.
 */
public abstract class AbstractAiAgentTest {
    /** The model every turn in this test runs against; script it with {@link ScriptedStreamingChatModel#enqueue}. */
    protected final ScriptedStreamingChatModel scriptedModel = new ScriptedStreamingChatModel();

    @MockBean(AiServiceManager.class)
    public AiServiceManager aiServiceManager() {
        AiServiceInterface service = mock(AiServiceInterface.class);
        when(service.streamingChatModel(any())).thenReturn(scriptedModel);
        AiServiceManager manager = mock(AiServiceManager.class);
        when(manager.getAiService(any())).thenReturn(service);
        when(manager.hasConfiguredProvider()).thenReturn(true);
        return manager;
    }

    @MockBean(DocsMcpToolProvider.class)
    public DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    /**
     * Clears the scripted responses between tests. Named distinctly from any subclass hook so a subclass
     * adding its own {@code @BeforeEach} does not hide this one.
     */
    @BeforeEach
    void resetScriptedModel() {
        scriptedModel.clear();
    }
}
