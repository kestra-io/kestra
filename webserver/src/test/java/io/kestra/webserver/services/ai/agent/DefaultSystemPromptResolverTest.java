package io.kestra.webserver.services.ai.agent;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultSystemPromptResolverTest {

    private final DefaultSystemPromptResolver resolver = new DefaultSystemPromptResolver();

    @Test
    void shouldAlwaysReturnTheBuiltInPrompt() {
        // OSS has no custom prompts: the built-in prompt is returned regardless of provider/mode
        assertThat(resolver.resolve(AgentMode.EDIT, "gpt", "built-in")).isEqualTo("built-in");
        assertThat(resolver.resolve(AgentMode.ASK, null, "built-in")).isEqualTo("built-in");
    }
}
