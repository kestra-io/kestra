package io.kestra.mcp;

import java.util.List;
import java.util.Map;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.webserver.services.ai.agent.DefaultAgentPrincipalResolver;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.annotation.MockBean;
import io.modelcontextprotocol.server.McpServerFeatures;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
@Property(name = "kestra.mcp.platform-server.enabled", value = "true")
class PlatformMcpToolSourceTest {
    @Inject
    PlatformMcpToolSource toolSource;

    @MockBean(DefaultAgentPrincipalResolver.class)
    DefaultAgentPrincipalResolver principalResolver() {
        DefaultAgentPrincipalResolver resolver = mock(DefaultAgentPrincipalResolver.class);
        when(resolver.reachableTenants("alice")).thenReturn(List.of("main", "sales"));
        when(resolver.reachableTenants("bob")).thenReturn(List.of("main"));
        return resolver;
    }

    @MockBean(DocsMcpToolProvider.class)
    DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    @Test
    void shouldListCallersTenantsAsTenantEnumWhenToolsAreBuilt() {
        List<McpServerFeatures.AsyncToolSpecification> tools = toolSource.view(context("alice")).tools();

        assertThat(tools).isNotEmpty().allSatisfy(tool ->
            assertThat(tool.tool().inputSchema().properties().get(PlatformMcpToolSource.TENANT_ARGUMENT))
                .asInstanceOf(InstanceOfAssertFactories.MAP)
                .containsEntry("enum", List.of("main", "sales"))
        );
    }

    @Test
    void shouldNotShareBuiltToolsWhenCallersReachDifferentTenants() {
        McpToolView alice = toolSource.view(context("alice"));
        McpToolView bob = toolSource.view(context("bob"));

        assertThat(alice.cacheKey()).isNotEqualTo(bob.cacheKey());
    }

    private static KestraMcpTransportContext context(String userId) {
        return KestraMcpTransportContext.builder().serverId("platform").userId(userId).build();
    }
}
