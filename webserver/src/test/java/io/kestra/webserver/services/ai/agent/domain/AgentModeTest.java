package io.kestra.core.ai.agent.models;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentModeTest {

    @Test
    void shouldExposeCumulativeToolFamiliesPerMode() {
        // Given / When / Then
        assertThat(AgentMode.ASK.allowedToolFamilies())
            .containsExactlyInAnyOrder(AgentToolFamily.READ);
        assertThat(AgentMode.EDIT.allowedToolFamilies())
            .containsExactlyInAnyOrder(AgentToolFamily.READ, AgentToolFamily.MUTATE);
        assertThat(AgentMode.PLAN.allowedToolFamilies())
            .containsExactlyInAnyOrder(AgentToolFamily.READ, AgentToolFamily.MUTATE, AgentToolFamily.ACT);
    }

    @Test
    void shouldKeepToolFamiliesCumulativeAcrossModes() {
        // Then: Ask ⊂ Edit ⊂ Plan
        assertThat(AgentMode.EDIT.allowedToolFamilies())
            .containsAll(AgentMode.ASK.allowedToolFamilies());
        assertThat(AgentMode.PLAN.allowedToolFamilies())
            .containsAll(AgentMode.EDIT.allowedToolFamilies());
    }

    @Test
    void shouldParseModeCaseInsensitively() {
        // Then
        assertThat(AgentMode.fromString("plan")).isEqualTo(AgentMode.PLAN);
        assertThat(AgentMode.fromString("Edit")).isEqualTo(AgentMode.EDIT);
    }

    @Test
    void shouldFailFastOnNullOrUnknownMode() {
        // Then: no UNKNOWN fallback — an unparseable value is a hard error
        assertThatThrownBy(() -> AgentMode.fromString(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AgentMode.fromString("teleport"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
