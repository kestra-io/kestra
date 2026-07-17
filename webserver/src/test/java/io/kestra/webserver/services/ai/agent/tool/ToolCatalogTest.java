package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCatalogTest {
    private static final AgentToolPermissionEvaluator ALLOW_ALL = (permission, tenant, principal) -> true;
    private static final AgentToolPermissionEvaluator DENY_ALL = (permission, tenant, principal) -> false;

    @Test
    void shouldExecuteToolWhenPermissionAllowed() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // When
        String result = catalog.dispatch(request("update-artefact"), AgentCallContext.Context.ofTenant("unit")).text();

        // Then
        assertThat(result).isEqualTo("updated: exec-1");
    }

    @Test
    void shouldDenyDispatchWhenCallerLacksToolPermission() {
        // Given — the enforcement point, independent of the ModeProfiles pre-filter
        ToolCatalog catalog = newCatalog(DENY_ALL);

        // When / Then — the tool never runs
        assertThatThrownBy(() -> catalog.dispatch(request("update-artefact"), AgentCallContext.Context.ofTenant("unit")))
            .isInstanceOf(ToolPermissionDeniedException.class)
            .hasMessageContaining("update-artefact")
            .hasMessageContaining("unit");
    }

    @Test
    void shouldCarryToolInstanceOnCatalogEntries() {
        // Given / When
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // Then — the entry exposes the tool instance so a permission evaluator can read its mapping
        ToolCatalog.ToolEntry entry = catalog.byName("update-artefact").orElseThrow();
        assertThat(entry.tool()).isInstanceOf(TestMutateTool.class);
        assertThat(entry.isPermissionEvaluated()).isTrue();
    }

    @Test
    void shouldRunToolAgainstCallerTenant() {
        // Given — the tenant is a property of the conversation, carried on the call context, never a tool arg
        ToolCatalog catalog = newCatalog(ALLOW_ALL);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("c1")
            .name("tenant-echo")
            .arguments("{}")
            .build();

        // When / Then — the tool reads the caller's tenant from the bound context
        assertThat(catalog.dispatch(request, AgentCallContext.Context.ofTenant("unit")).text()).isEqualTo("unit");
    }

    @Test
    void shouldExecuteSubclassOverrideOfInheritedToolMethod() {
        // Given — a bean that overrides the @Tool method without repeating the annotation, mirroring
        // how a replacement subclass extends a base tool
        DocsMcpToolProvider docs = mock(DocsMcpToolProvider.class);
        when(docs.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(List.of(new OverridingEchoTool()), List.of(), docs, ALLOW_ALL);
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("c1").name("tenant-echo").arguments("{}").build();

        // When — the spec is derived from the inherited @Tool method, execution dispatches virtually
        String result = catalog.dispatch(request, AgentCallContext.Context.ofTenant("unit")).text();

        // Then — the subclass override ran
        assertThat(result).isEqualTo("overridden:unit");
    }

    @Test
    void shouldThrowWhenToolUnknown() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // When / Then
        assertThatThrownBy(() -> catalog.dispatch(request("no-such-tool"), AgentCallContext.Context.ofTenant("unit")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no-such-tool");
    }

    @Test
    void shouldSurfaceArtefactWhenToolReturnsPublishableResult() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // When
        ToolCatalog.DispatchResult result = catalog.dispatch(request("draft-artefact"), AgentCallContext.Context.ofTenant("unit"));

        // Then — the publishable artefact is surfaced for the orchestrator to persist and stream
        assertThat(result.artefact()).isNotNull();
        assertThat(result.artefact().draftId()).isEqualTo("draft-exec-1");
    }

    @Test
    void shouldSurfaceNoArtefactForNonPublishableResult() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // When
        ToolCatalog.DispatchResult result = catalog.dispatch(request("update-artefact"), AgentCallContext.Context.ofTenant("unit"));

        // Then
        assertThat(result.artefact()).isNull();
        assertThat(result.text()).isEqualTo("updated: exec-1");
    }

    private static ToolCatalog newCatalog(final AgentToolPermissionEvaluator evaluator) {
        DocsMcpToolProvider docs = mock(DocsMcpToolProvider.class);
        when(docs.tools()).thenReturn(Map.of());
        return new ToolCatalog(List.of(new TestMutateTool(), new TestTenantEchoTool()), List.of(new TestDraftTool()), docs, evaluator);
    }

    private static ToolExecutionRequest request(final String name) {
        return ToolExecutionRequest.builder()
            .id("c1")
            .name(name)
            .arguments("{\"executionId\":\"exec-1\"}")
            .build();
    }

    /** Overrides the inherited {@code @Tool} method without re-annotating it — like a replacement subclass. */
    private static final class OverridingEchoTool extends TestTenantEchoTool {
        @Override
        public String tenantEcho(final AgentCallContext.Context context) {
            return "overridden:" + super.tenantEcho(context);
        }
    }
}
