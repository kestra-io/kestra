package io.kestra.webserver.services.ai.gemini;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiConfigurationTest {
    @Test
    void shouldRejectThinkingEffortNone() {
        Map<String, Object> configuration = Map.of("apiKey", "fake-key", "thinkingEffort", "none");

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(configuration, GeminiConfiguration.class))
            .hasRootCauseMessage("Gemini has no 'none' thinking level, use 'low' or set thinking-enabled to false instead.");
    }
}
