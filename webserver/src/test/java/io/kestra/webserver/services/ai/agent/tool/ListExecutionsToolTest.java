package io.kestra.webserver.services.ai.agent.tool;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.webserver.services.ai.agent.AgentCallContext;
import io.kestra.webserver.services.ai.agent.domain.AgentToolFamily;
import io.kestra.webserver.services.ai.agent.domain.AgentWritePolicy;

import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListExecutionsToolTest {
    private static final String TENANT = "main";

    private ExecutionRepositoryInterface executionRepository;
    private ListExecutionsTool tool;

    @BeforeEach
    void setUp() {
        executionRepository = mock(ExecutionRepositoryInterface.class);
        tool = new ListExecutionsTool(executionRepository);
        AgentCallContext.set(AgentCallContext.Context.ofTenant(TENANT));
    }

    @AfterEach
    void tearDown() {
        AgentCallContext.clear();
    }

    @Test
    void shouldExposeReadOnlyMetadata() {
        // When / Then
        assertThat(tool.family()).isEqualTo(AgentToolFamily.READ);
        assertThat(tool.writePolicy()).isEqualTo(AgentWritePolicy.AUTO);
    }

    @Test
    void shouldReturnOneSummaryPerExecutionWhenExecutionsExist() {
        // Given — a terminated execution with a duration
        State state = State.of(
            State.Type.SUCCESS, List.of(
                new State.History(State.Type.CREATED, Instant.parse("2026-01-01T00:00:00Z")),
                new State.History(State.Type.SUCCESS, Instant.parse("2026-01-01T00:00:10Z"))
            )
        );
        Execution execution = Execution.builder()
            .id("exec-1")
            .tenantId(TENANT)
            .namespace("io.kestra.test")
            .flowId("flow-1")
            .state(state)
            .build();
        when(executionRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(execution), 1));

        // When
        List<QueryFilter> filters = List.of(new QueryFilter(QueryFilter.Field.STATE, QueryFilter.Op.EQUALS, "SUCCESS", null, null));
        ListExecutionsTool.Result result = tool.listExecutions(filters, null);

        // Then — one summary, and the filters plus a start-date-desc pageable were forwarded
        assertThat(result.executions()).containsExactly(
            new ListExecutionsTool.ExecutionSummary("exec-1", "io.kestra.test", "flow-1", "SUCCESS", "2026-01-01T00:00:00Z", "PT10S")
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(executionRepository).find(pageableCaptor.capture(), eq(TENANT), eq(filters));
        assertThat(pageableCaptor.getValue().getSize()).isEqualTo(50);
        assertThat(pageableCaptor.getValue().getSort().getOrderBy())
            .singleElement()
            .satisfies(order ->
            {
                assertThat(order.getProperty()).isEqualTo(Execution.STATE_START_DATE_FIELD);
                assertThat(order.getDirection()).isEqualTo(io.micronaut.data.model.Sort.Order.Direction.DESC);
            });
    }

    @Test
    void shouldReturnEmptyListWhenNoExecutionsMatch() {
        // Given
        when(executionRepository.find(any(Pageable.class), eq(TENANT), any()))
            .thenReturn(new ArrayListTotal<>(List.of(), 0));

        // When
        ListExecutionsTool.Result result = tool.listExecutions(null, null);

        // Then
        assertThat(result.executions()).isEmpty();
    }
}
