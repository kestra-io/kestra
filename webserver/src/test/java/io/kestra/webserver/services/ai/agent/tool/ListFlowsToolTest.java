package io.kestra.webserver.services.ai.agent.tool;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
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

class ListFlowsToolTest {
    private static final String TENANT = "main";

    private FlowRepositoryInterface flowRepository;
    private ListFlowsTool tool;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowRepositoryInterface.class);
        tool = new ListFlowsTool(flowRepository);
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
    void shouldFormatOneLinePerFlowWhenFlowsExist() {
        // Given — one flow with a description (containing a newline) and one without
        Flow withDescription = Flow.builder()
            .tenantId(TENANT)
            .namespace("io.kestra.test")
            .id("flow-1")
            .description("Loads the daily\nsales report")
            .build();
        Flow withoutDescription = Flow.builder()
            .tenantId(TENANT)
            .namespace("io.kestra.other")
            .id("flow-2")
            .build();
        when(flowRepository.find(any(Pageable.class), eq(TENANT), ArgumentMatchers.<List<QueryFilter>> any()))
            .thenReturn(new ArrayListTotal<>(List.of(withDescription, withoutDescription), 2));

        // When
        List<QueryFilter> filters = List.of(new QueryFilter(QueryFilter.Field.NAMESPACE, QueryFilter.Op.EQUALS, "io.kestra.test", null, null));
        ListFlowsTool.Result result = tool.listFlows(filters, null);

        // Then — one summary per flow
        assertThat(result.flows()).containsExactly(
            new ListFlowsTool.FlowSummary("io.kestra.test", "flow-1", "Loads the daily\nsales report"),
            new ListFlowsTool.FlowSummary("io.kestra.other", "flow-2", null)
        );
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(flowRepository).find(pageableCaptor.capture(), eq(TENANT), eq(filters));
        assertThat(pageableCaptor.getValue().getSize()).isEqualTo(50);
    }

    @Test
    void shouldReturnEmptyListWhenNoFlowsMatch() {
        // Given
        when(flowRepository.find(any(Pageable.class), eq(TENANT), ArgumentMatchers.<List<QueryFilter>> any()))
            .thenReturn(new ArrayListTotal<>(List.of(), 0));

        // When
        ListFlowsTool.Result result = tool.listFlows(null, null);

        // Then
        assertThat(result.flows()).isEmpty();
    }
}
