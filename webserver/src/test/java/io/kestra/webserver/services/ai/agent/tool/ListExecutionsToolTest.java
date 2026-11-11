package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.ai.agent.models.AgentToolFamily;
import io.kestra.core.ai.agent.models.AgentWritePolicy;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.ai.agent.AgentCallContext;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration check that {@code list_executions} returns real executions from the repository,
 * including when a state filter is supplied, exercising the tool → repository wiring end to end.
 */
@KestraTest(environments = "memory")
class ListExecutionsToolTest {
    private static final String NAMESPACE = "io.kestra.test.ai";
    private static final AgentCallContext.Context CONTEXT = AgentCallContext.Context.ofTenant(MAIN_TENANT);

    @Inject
    private ListExecutionsTool tool;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Test
    void shouldExposeReadOnlyMetadata() {
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnSummaryPerExecutionWhenExecutionsExist() {
        // Given — a terminated execution
        String executionId = save(State.Type.SUCCESS);

        // When
        ListExecutionsTool.Result result = tool.listExecutions(List.of(), CONTEXT);

        // Then
        assertThat(result.executions())
            .anyMatch(summary -> executionId.equals(summary.id()) && "SUCCESS".equals(summary.state()));
    }

    @Test
    void shouldApplyStateFilter() {
        // Given — a SUCCESS and a FAILED execution
        String successId = save(State.Type.SUCCESS);
        save(State.Type.FAILED);

        // When — filtering on SUCCESS
        List<QueryFilter> filters = List.of(new QueryFilter(QueryFilter.Field.STATE, QueryFilter.Op.EQUALS, "SUCCESS", null, null));
        ListExecutionsTool.Result result = tool.listExecutions(filters, CONTEXT);

        // Then — only SUCCESS executions are returned, including the one we created
        assertThat(result.executions()).isNotEmpty();
        assertThat(result.executions()).allMatch(summary -> "SUCCESS".equals(summary.state()));
        assertThat(result.executions()).anyMatch(summary -> successId.equals(summary.id()));
    }

    @Test
    void shouldReturnTotalBeyondReturnedExecutions() throws Exception {
        // Given — more executions in a namespace than the tool returns
        String tenant = "listexecutionstotal";
        int executionCount = 52;
        for (int i = 0; i < executionCount; i++) {
            save(tenant, State.Type.SUCCESS);
        }
        QueryFilterToolExecutor executor = new QueryFilterToolExecutor(
            tool,
            ListExecutionsTool.class.getMethod("listExecutions", List.class, AgentCallContext.Context.class)
        );
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .id("call-1")
            .name("list_executions")
            .arguments("""
                { "NAMESPACE": { "operator": "EQUALS", "value": "%s" } }""".formatted(NAMESPACE))
            .build();

        // When — the model calls the tool as it would through the catalog
        String json = executor.executeWithContext(request, AgentCallContext.into(AgentCallContext.Context.ofTenant(tenant))).resultText();

        // Then — the model sees one page of executions plus the real total
        Map<String, Object> result = JacksonMapper.toMap(json);
        assertThat((List<?>) result.get("executions")).hasSize(50);
        assertThat(((Number) result.get("total")).longValue()).isEqualTo(executionCount);
    }

    private String save(final State.Type stateType) {
        return save(MAIN_TENANT, stateType);
    }

    private String save(final String tenant, final State.Type stateType) {
        String executionId = IdUtils.create();
        State state = State.of(
            stateType, List.of(
                new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                new State.History(stateType, Instant.parse("2026-01-01T00:00:10Z"))
            )
        );
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .tenantId(tenant)
                .namespace(NAMESPACE)
                .flowId("flow-1")
                .state(state)
                .build()
        );
        return executionId;
    }
}
