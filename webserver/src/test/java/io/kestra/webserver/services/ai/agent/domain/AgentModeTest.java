package io.kestra.webserver.services.ai.agent.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
    void shouldParseModeCaseInsensitivelyAndTolerateNull() {
        // Then
        assertThat(AgentMode.fromString("plan")).isEqualTo(AgentMode.PLAN);
        assertThat(AgentMode.fromString("  Edit ")).isEqualTo(AgentMode.EDIT);
        assertThat(AgentMode.fromString(null)).isNull();
    }
}
