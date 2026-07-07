package io.kestra.webserver.services.ai.agent;

import java.util.List;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModeProfilesTest {

    private final ModeProfiles modeProfiles = newModeProfiles();

    @Test
    void shouldLoadANonBlankSystemPromptForEveryMode() {
        // Given / When / Then: every mode maps to a prompt resource that loads and mentions its mode
        for (AgentMode mode : AgentMode.values()) {
            String prompt = modeProfiles.resolve(mode).systemPrompt();
            assertThat(prompt).as("system prompt for %s", mode).isNotBlank();
            assertThat(prompt).as("prompt for %s names its mode", mode).containsIgnoringCase(mode.name());
        }
    }

    @Test
    void shouldLoadADistinctPromptPerMode() {
        // Then: no two modes accidentally resolve to the same prompt file
        assertThat(List.of(
            modeProfiles.resolve(AgentMode.ASK).systemPrompt(),
            modeProfiles.resolve(AgentMode.EDIT).systemPrompt(),
            modeProfiles.resolve(AgentMode.PLAN).systemPrompt()
        )).doesNotHaveDuplicates();
    }

    private static ModeProfiles newModeProfiles() {
        // Prompt loading is independent of the catalog, so an empty one is enough here.
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.entries()).thenReturn(List.of());
        return new ModeProfiles(catalog);
    }
}
