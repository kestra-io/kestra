package io.kestra.webserver.services.ai.agent.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolCatalogTest {
    private static final AgentToolPermissionEvaluator ALLOW_ALL = (permission, tenant, principal) -> true;
    private static final AgentToolPermissionEvaluator DENY_ALL = (permission, tenant, principal) -> false;

    /** Default OSS factory hides the tenant parameter; the multi-tenant one exposes it. */
    private static final AiToolSpecFactory HIDE_TENANT = new DefaultAiToolSpecFactory();
    private static final AiToolSpecFactory EXPOSE_TENANT = method -> AiToolSpecifications.toolSpecificationFrom(method, true);

    @Test
    void shouldExecuteToolWhenPermissionAllowed() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);

        // When
        String result = catalog.dispatch(request("update-artefact"), AgentCallContext.Context.ofTenant("unit"));

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

        // Then — the entry exposes the tool instance so the EE evaluator can read its RBAC mapping
        ToolCatalog.ToolEntry entry = catalog.byName("update-artefact").orElseThrow();
        assertThat(entry.tool()).isInstanceOf(TestMutateTool.class);
        assertThat(entry.isPermissionEvaluated()).isTrue();
    }

    @Test
    void shouldHideTenantIdFromSpecWithDefaultFactory() {
        // Given — the default (OSS) spec factory
        ToolCatalog catalog = newCatalog(ALLOW_ALL, HIDE_TENANT);

        // When
        JsonObjectSchema params = (JsonObjectSchema) catalog.byName("tenant-echo").orElseThrow().specification().parameters();

        // Then — the @TenantId parameter is not advertised to the model
        assertThat(params.properties()).doesNotContainKey("tenantId");
    }

    @Test
    void shouldExposeTenantIdInSpecWithMultiTenantFactory() {
        // Given — a spec factory that exposes tenant targeting (as EE does)
        ToolCatalog catalog = newCatalog(ALLOW_ALL, EXPOSE_TENANT);

        // When
        JsonObjectSchema params = (JsonObjectSchema) catalog.byName("tenant-echo").orElseThrow().specification().parameters();

        // Then — the parameter is advertised so a multi-tenant caller can target another tenant
        assertThat(params.properties()).containsKey("tenantId");
    }

    @Test
    void shouldBindTenantIdEvenWhenHiddenFromSpec() {
        // Given — an evaluator recording the tenant it is asked about
        List<String> checkedTenants = new ArrayList<>();
        AgentToolPermissionEvaluator recording = (permission, tenant, principal) ->
        {
            checkedTenants.add(tenant);
            return true;
        };
        ToolCatalog catalog = newCatalog(recording);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("c1")
            .name("tenant-echo")
            .arguments("{\"tenantId\":\"other-tenant\"}")
            .build();

        // When — the caller's own tenant is 'unit' but the call targets 'other-tenant'
        String result = catalog.dispatch(request, AgentCallContext.Context.ofTenant("unit"));

        // Then — the coarse gate checks only the caller's own tenant (resolveTenant is plumbing),
        // yet the parameter still binds and the tool runs against the requested tenant
        assertThat(checkedTenants).containsExactly("unit");
        assertThat(result).isEqualTo("other-tenant");
    }

    @Test
    void shouldUseCallerTenantWhenNoTenantIdProvided() {
        // Given
        ToolCatalog catalog = newCatalog(ALLOW_ALL);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("c1")
            .name("tenant-echo")
            .arguments("{}")
            .build();

        // When / Then
        assertThat(catalog.dispatch(request, AgentCallContext.Context.ofTenant("unit"))).isEqualTo("unit");
    }

    @Test
    void shouldExecuteSubclassOverrideOfInheritedToolMethod() {
        // Given — a bean that overrides the @Tool method without repeating the annotation, mirroring
        // how an EE @Replaces subclass extends an OSS tool
        DocsMcpToolProvider docs = mock(DocsMcpToolProvider.class);
        when(docs.tools()).thenReturn(Map.of());
        ToolCatalog catalog = new ToolCatalog(List.of(new OverridingEchoTool()), List.of(), docs, ALLOW_ALL, HIDE_TENANT);
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("c1").name("tenant-echo").arguments("{}").build();

        // When — the spec is derived from the inherited @Tool method, execution dispatches virtually
        String result = catalog.dispatch(request, AgentCallContext.Context.ofTenant("unit"));

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

    private static ToolCatalog newCatalog(final AgentToolPermissionEvaluator evaluator) {
        return newCatalog(evaluator, HIDE_TENANT);
    }

    private static ToolCatalog newCatalog(final AgentToolPermissionEvaluator evaluator, final AiToolSpecFactory specFactory) {
        DocsMcpToolProvider docs = mock(DocsMcpToolProvider.class);
        when(docs.tools()).thenReturn(Map.of());
        return new ToolCatalog(List.of(new TestMutateTool(), new TestTenantEchoTool()), List.of(new TestDraftTool()), docs, evaluator, specFactory);
    }

    private static ToolExecutionRequest request(final String name) {
        return ToolExecutionRequest.builder()
            .id("c1")
            .name(name)
            .arguments("{\"executionId\":\"exec-1\"}")
            .build();
    }

    /** Overrides the inherited {@code @Tool} method without re-annotating it — like an EE subclass. */
    private static final class OverridingEchoTool extends TestTenantEchoTool {
        @Override
        public String tenantEcho(final String tenantId) {
            return "overridden:" + super.tenantEcho(tenantId);
        }
    }
}
