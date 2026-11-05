package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
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

    @Test
    void shouldReturnTotalBeyondReturnedFlows() throws Exception {
        // Given — more flows in a namespace than the tool returns
        String tenant = "listflowstotal";
        String namespace = "io.kestra.test.ai.total";
        int flowCount = 52;
        for (int i = 0; i < flowCount; i++) {
            createFlowIn(tenant, namespace, IdUtils.create());
        }
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(
            tool,
            ListFlowsTool.class.getMethod("listFlows", List.class, AgentCallContext.Context.class)
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("list_flows")
            .arguments("""
                { "NAMESPACE": { "operator": "EQUALS", "value": "%s" } }""".formatted(namespace))
            .build();

        // When — the model calls the tool as it would through the catalog
        String json = executor.executeWithContext(request, AgentCallContext.into(AgentCallContext.Context.ofTenant(tenant))).resultText();

        // Then — the model sees one page of flows plus the real total
        Map<String, Object> result = JacksonMapper.toMap(json);
        assertThat((List<?>) result.get("flows")).hasSize(50);
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(flowCount);
    }

    @Test
    void shouldMatchEveryListedNamespaceWhenInValueIsCommaSeparated() throws Exception {
        // Given
        String tenant = "listflowsin";
        String first = createFlowIn(tenant, "io.kestra.test.ai.in.first", IdUtils.create());
        String second = createFlowIn(tenant, "io.kestra.test.ai.in.second", IdUtils.create());
        createFlowIn(tenant, "io.kestra.test.ai.in.other", IdUtils.create());

        // When
        String json = callListFlows(tenant, """
            { "NAMESPACE": { "operator": "IN", "value": "io.kestra.test.ai.in.first, io.kestra.test.ai.in.second" } }""");

        // Then
        List<String> ids = ((List<?>) JacksonMapper.toMap(json).get("flows")).stream()
            .map(flow -> String.valueOf(((Map<?, ?>) flow).get("id")))
            .toList();
        assertThat(ids).containsExactlyInAnyOrder(first, second);
    }

    private String callListFlows(final String tenant, final String arguments) throws Exception {
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(
            tool,
            ListFlowsTool.class.getMethod("listFlows", List.class, AgentCallContext.Context.class)
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("list_flows")
            .arguments(arguments)
            .build();
        return executor.executeWithContext(request, AgentCallContext.into(AgentCallContext.Context.ofTenant(tenant))).resultText();
    }

    private void createFlow(final String flowId) {
        createFlowIn(NAMESPACE, flowId);
    }

    private void createFlowIn(final String namespace, final String flowId) {
        createFlowIn(MAIN_TENANT, namespace, flowId);
    }

    private String createFlowIn(final String tenant, final String namespace, final String flowId) {
        flowRepository.create(
            GenericFlow.of(
                Flow.builder()
                    .id(flowId)
                    .namespace(namespace)
                    .tenantId(tenant)
                    .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("ok")).build()))
                    .build()
            )
        );
        return flowId;
    }
}
