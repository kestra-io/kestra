package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(environments = "memory")
class ListFlowsToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ListFlowsTool tool;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldListFlowsWhenInvoked() {
        // Given — a real flow
        String flowId = IdUtils.create();
        createFlow(flowId);

        // When
        ListFlowsTool.Result result = tool.listFlows(List.of(), CONTEXT);

        // Then
        assertThat(result.flows())
            .anyMatch(flow -> flowId.equals(flow.id()) && NAMESPACE.equals(flow.namespace()));
    }

    @Test
    void shouldApplyNamespaceFilter() {
        // Given — a flow in the target namespace and one elsewhere
        String flowId = IdUtils.create();
        createFlow(flowId);
        createFlowIn("io.kestra.test.other", IdUtils.create());

        // When — filtering on the target namespace
        List<QueryFilter> filters = List.of(new QueryFilter(QueryFilter.Field.NAMESPACE, QueryFilter.Op.EQUALS, NAMESPACE, null, null));
        ListFlowsTool.Result result = tool.listFlows(filters, CONTEXT);

        // Then — only the target namespace is returned
        assertThat(result.flows()).isNotEmpty();
        assertThat(result.flows()).allMatch(flow -> NAMESPACE.equals(flow.namespace()));
    }

    private void createFlow(final String flowId) {
        createFlowIn(NAMESPACE, flowId);
    }

    private void createFlowIn(final String namespace, final String flowId) {
        flowRepository.create(
            GenericFlow.of(
                Flow.builder()
                    .id(flowId)
                    .namespace(namespace)
                    .tenantId(MAIN_TENANT)
                    .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
                    .build()
            )
        );
    }
}
