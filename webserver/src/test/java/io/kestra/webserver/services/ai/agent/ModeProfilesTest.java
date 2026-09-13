package io.kestra.webserver.services.ai.agent;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentToolCall;
import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.ai.agent.models.ArtefactKind;
import io.kestra.webserver.services.ai.agent.tool.AgentToolPermissionEvaluator;
import io.kestra.webserver.services.ai.agent.tool.AiAuthoringTool;
import io.kestra.webserver.services.ai.agent.tool.AiPlatformTool;
import io.kestra.webserver.services.ai.agent.tool.ToolCatalog;

import dev.langchain4j.agent.tool.ToolSpecification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModeProfilesTest {
    private static final String TENANT = "unit";
    private static final AgentToolPermissionEvaluator ALLOW_ALL = (permission, tenant, principal) -> true;

    private final ModeProfiles modeProfiles = newModeProfiles(List.of(), ALLOW_ALL);

    @Test
    void shouldLoadANonBlankSystemPromptForEveryMode() {
        // Given / When / Then: every mode maps to a prompt resource that loads and mentions its mode
        for (AgentMode mode : AgentMode.values()) {
            String prompt = modeProfiles.resolve(mode, TENANT, null).systemPrompt();
            assertThat(prompt).as("system prompt for %s", mode).isNotBlank();
            assertThat(prompt).as("prompt for %s names its mode", mode).containsIgnoringCase(mode.name());
        }
    }

    @Test
    void shouldLoadADistinctPromptPerMode() {
        // Then: no two modes accidentally resolve to the same prompt file
        assertThat(
            List.of(
                modeProfiles.resolve(AgentMode.ASK, TENANT, null).systemPrompt(),
                modeProfiles.resolve(AgentMode.EDIT, TENANT, null).systemPrompt(),
                modeProfiles.resolve(AgentMode.PLAN, TENANT, null).systemPrompt()
            )
        ).doesNotHaveDuplicates();
    }

    @Test
    void shouldAdvertiseAuthoringToolsInEveryModeIncludingAsk() {
        // Given — a READ platform tool and an authoring tool (no family)
        List<ToolCatalog.ToolEntry> entries = List.of(
            entry("read-flow", new TestReadTool()),
            authoringEntry("author-flow")
        );

        // When / Then — authoring escapes family gating: Ask advertises it alongside the reads
        for (AgentMode mode : AgentMode.values()) {
            assertThat(newModeProfiles(entries, ALLOW_ALL).resolve(mode, TENANT, null).allowedToolNames())
                .as("mode %s", mode)
                .contains("author-flow");
        }
    }

    @Test
    void shouldAdvertiseOnlyPermittedToolsWhenEvaluatorDeniesAPermission() {
        // Given — two platform READ tools, one of which the caller may not use, and a docs MCP entry
        // (no platform tool) that is outside permission evaluation
        List<ToolCatalog.ToolEntry> entries = List.of(
            entry("read-flow", new TestReadTool()),
            entry("read-execution", new TestReadTool()),
            entry("search-docs", null)
        );
        AgentToolPermissionEvaluator denyExecutions = (entry, tenant, principal) -> !"read-execution".equals(entry.name());

        // When — the coarse specsFor(mode, principal) pre-filter resolves the Ask profile
        ModeProfiles.ResolvedProfile profile = newModeProfiles(entries, denyExecutions)
            .resolve(AgentMode.ASK, TENANT, null);

        // Then — the denied tool is not advertised nor runtime-allowed; permission-less tools always are
        assertThat(profile.allowedToolNames()).containsExactlyInAnyOrder("read-flow", "search-docs");
        assertThat(profile.toolSpecifications()).extracting(ToolSpecification::name)
            .containsExactlyInAnyOrder("read-flow", "search-docs");
    }

    private static ModeProfiles newModeProfiles(final List<ToolCatalog.ToolEntry> entries, final AgentToolPermissionEvaluator evaluator) {
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.entries()).thenReturn(entries);
        return new ModeProfiles(catalog, evaluator);
    }

    private static ToolCatalog.ToolEntry entry(final String name, final AiPlatformTool tool) {
        return new ToolCatalog.ToolEntry(
            name, ToolSpecification.builder().name(name).description(name).build(),
            (request, memoryId) -> "ok", AgentToolCall.Kind.PLATFORM, AgentToolFamily.READ, AgentWritePolicy.AUTO, tool
        );
    }

    private static ToolCatalog.ToolEntry authoringEntry(final String name) {
        return new ToolCatalog.ToolEntry(
            name, ToolSpecification.builder().name(name).description(name).build(),
            (request, memoryId) -> "ok", AgentToolCall.Kind.AUTHORING, null, AgentWritePolicy.AUTO,
            (AiAuthoringTool) () -> ArtefactKind.FLOW
        );
    }

    private static final class TestReadTool implements AiPlatformTool {
        @Override
        public AgentToolFamily family() {
            return AgentToolFamily.READ;
        }

        @Override
        public AgentWritePolicy writePolicy() {
            return AgentWritePolicy.AUTO;
        }
    }
}
