package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.services.ai.AiProviderConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolResultBudgetTest {

    @Test
    void shouldUseInstanceDefaultWhenProviderHasNoOverride() {
        ToolResultBudget budget = new ToolResultBudget(5_000, List.of(provider("plain", Map.of())));

        assertThat(budget.limitFor("plain")).isEqualTo(5_000);
        assertThat(budget.limitFor(null)).isEqualTo(5_000);
        assertThat(budget.limitFor("unknown")).isEqualTo(5_000);
    }

    @Test
    void shouldUseProviderOverrideWhenConfigured() {
        ToolResultBudget budget = new ToolResultBudget(5_000, List.of(
            provider("numeric", Map.of("maxToolResultChars", 2_000)),
            provider("text", Map.of("maxToolResultChars", "3000"))
        ));

        assertThat(budget.limitFor("numeric")).isEqualTo(2_000);
        assertThat(budget.limitFor("text")).isEqualTo(3_000);
    }

    @Test
    void shouldRejectOverrideThatIsNotAPositiveNumber() {
        assertThatThrownBy(() -> new ToolResultBudget(5_000, List.of(provider("bad", Map.of("maxToolResultChars", "lots")))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bad")
            .hasMessageContaining("maxToolResultChars");
    }

    @Test
    void shouldRejectNonPositiveInstanceDefault() {
        assertThatThrownBy(() -> new ToolResultBudget(0, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("max-tool-result-chars");
    }

    private static AiProviderConfiguration provider(final String id, final Map<String, Object> configuration) {
        return new AiProviderConfiguration(id, id, "gemini", false, configuration);
    }
}
