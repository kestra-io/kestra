package io.kestra.core.services;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.repositories.ExecutionRepositoryInterface;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogsExecutionFilterServiceTest {
    private static final String TENANT = "main";

    private final ExecutionRepositoryInterface executionRepository = mock(ExecutionRepositoryInterface.class);

    @Test
    void shouldReplaceExecutionFiltersWithTheIdsOfTheMatchingExecutions() {
        // Given
        when(executionRepository.findAsync(eq(TENANT), any())).thenReturn(Flux.just(execution("e1"), execution("e2")));
        var namespace = filter(Field.NAMESPACE, Op.EQUALS, "company");
        var level = filter(Field.LEVEL, Op.EQUALS, "ERROR");
        var state = filter(Field.STATE, Op.IN, List.of("FAILED"));
        var labels = filter(Field.LABELS, Op.EQUALS, Map.of("team", "data"));

        // When
        var resolved = new LogsExecutionFilterService(executionRepository, 10)
            .resolve(TENANT, List.of(namespace, level, state, labels), ZonedDateTime.now().minusDays(1), ZonedDateTime.now());

        // Then
        assertThat(resolved).containsExactly(namespace, level, filter(Field.EXECUTION_ID, Op.IN, List.of("e1", "e2")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QueryFilter>> lookup = ArgumentCaptor.forClass(List.class);
        verify(executionRepository).findAsync(eq(TENANT), lookup.capture());
        assertThat(lookup.getValue()).extracting(QueryFilter::field)
            .containsExactlyInAnyOrder(Field.STATE, Field.LABELS, Field.NAMESPACE, Field.START_DATE, Field.START_DATE);
    }

    @Test
    void shouldKeepTheFiltersWhenMoreExecutionsMatchThanTheLimit() {
        // Given
        when(executionRepository.findAsync(eq(TENANT), any())).thenReturn(Flux.just(execution("e1"), execution("e2"), execution("e3")));
        var filters = List.of(filter(Field.STATE, Op.IN, List.of("FAILED")));

        // When
        var resolved = new LogsExecutionFilterService(executionRepository, 2).resolve(TENANT, filters, null, null);

        // Then
        assertThat(resolved).isSameAs(filters);
    }

    @Test
    void shouldNotLookUpExecutionsWhenNoFilterDescribesAnExecution() {
        // Given
        var filters = List.of(filter(Field.NAMESPACE, Op.EQUALS, "company"));

        // When
        var resolved = new LogsExecutionFilterService(executionRepository, 10).resolve(TENANT, filters, null, null);

        // Then
        assertThat(resolved).isSameAs(filters);
        verifyNoInteractions(executionRepository);
    }

    private static Execution execution(String id) {
        return Execution.builder().id(id).build();
    }

    private static QueryFilter filter(Field field, Op operation, Object value) {
        return QueryFilter.builder().field(field).operation(operation).value(value).build();
    }
}
