package io.kestra.webserver.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.searchable.SearchableOutputRepositoryInterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionOutputFilterServiceTest {
    private final SearchableOutputRepositoryInterface repository = mock(SearchableOutputRepositoryInterface.class);
    private final ExecutionOutputFilterService service = new ExecutionOutputFilterService(repository);

    private static QueryFilter outputs(Op operation, Object value) {
        return QueryFilter.builder().field(Field.OUTPUTS).operation(operation).value(value).build();
    }

    private static QueryFilter idIn(String... ids) {
        return QueryFilter.builder().field(Field.ID).operation(Op.IN).value(List.of(ids)).build();
    }

    @Test
    void shouldReplaceOutputsFilterByIdFilter() {
        // Given
        when(repository.findExecutionIds(eq("main"), eq("orderIds"), eq(Op.EQUALS), eq("ORD-8841"), anyInt())).thenReturn(List.of("exec1", "exec2"));
        QueryFilter namespace = QueryFilter.builder().field(Field.NAMESPACE).operation(Op.EQUALS).value("ns").build();

        // When
        List<QueryFilter> resolved = service.resolve("main", List.of(namespace, outputs(Op.EQUALS, Map.of("orderIds", "ORD-8841"))));

        // Then
        assertThat(resolved).containsExactly(namespace, idIn("exec1", "exec2"));
    }

    @Test
    void shouldReplaceOutputsFilterNestedInALogicalNode() {
        // Given
        when(repository.findExecutionIds(eq("main"), anyString(), eq(Op.EQUALS), any(), anyInt())).thenReturn(List.of("exec1"));
        QueryFilter node = QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(List.of(outputs(Op.EQUALS, Map.of("a", "x")), outputs(Op.EQUALS, Map.of("b", "y"))))
            .build();

        // When
        List<QueryFilter> resolved = service.resolve("main", List.of(node));

        // Then
        assertThat(resolved.getFirst().children()).allSatisfy(child -> assertThat(child.field()).isEqualTo(Field.ID));
    }

    @Test
    void shouldMatchNothingWhenNoExecutionHasTheOutput() {
        // Given
        when(repository.findExecutionIds(eq("main"), anyString(), eq(Op.EQUALS), any(), anyInt())).thenReturn(List.of());

        // When
        List<QueryFilter> resolved = service.resolve("main", List.of(outputs(Op.EQUALS, Map.of("output", "value"))));

        // Then
        assertThat(resolved.getFirst().value()).isEqualTo(List.of());
    }

    @Test
    void shouldKeepFiltersUntouchedWhenThereIsNoOutputsFilter() {
        List<QueryFilter> filters = List.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.EQUALS).value("ns").build());

        assertThat(service.resolve("main", filters)).isSameAs(filters);
    }

    @Test
    void shouldSplitTheValuesOfMultiValuedOperations() {
        // Given
        when(repository.findExecutionIds(eq("main"), eq("output"), eq(Op.IN), eq(List.of("a", "b")), anyInt())).thenReturn(List.of("exec1"));
        when(repository.findExecutionIds(eq("main"), eq("output"), eq(Op.NOT_IN), eq(List.of("a", "b")), anyInt())).thenReturn(List.of("exec2"));

        // When
        List<QueryFilter> in = service.resolve("main", List.of(outputs(Op.IN, Map.of("output", "a,b"))));
        List<QueryFilter> notIn = service.resolve("main", List.of(outputs(Op.NOT_IN, Map.of("output", "a,b"))));

        // Then
        assertThat(in).containsExactly(idIn("exec1"));
        assertThat(notIn).containsExactly(idIn("exec2"));
    }

    @Test
    void shouldNotSplitTheValueOfSingleValueOperations() {
        // Given
        when(repository.findExecutionIds(eq("main"), eq("output"), eq(Op.EQUALS), eq("a,b"), anyInt())).thenReturn(List.of("exec1"));

        // When
        List<QueryFilter> resolved = service.resolve("main", List.of(outputs(Op.EQUALS, Map.of("output", "a,b"))));

        // Then
        assertThat(resolved).containsExactly(idIn("exec1"));
    }

    @Test
    void shouldResolveNegativeOperationOnTheOutputsNotOnTheExecutions() {
        // Given
        when(repository.findExecutionIds(eq("main"), eq("output"), eq(Op.NOT_EQUALS), eq("a"), anyInt())).thenReturn(List.of("exec1"));

        // When
        List<QueryFilter> resolved = service.resolve("main", List.of(outputs(Op.NOT_EQUALS, Map.of("output", "a"))));

        // Then
        assertThat(resolved).containsExactly(idIn("exec1"));
    }

    @Test
    void shouldFailWhenOutputIdIsMissing() {
        assertThatThrownBy(() -> service.resolve("main", List.of(outputs(Op.EQUALS, "ORD-8841"))))
            .isInstanceOf(InvalidQueryFiltersException.class);
    }

    @Test
    void shouldFailWhenTooManyExecutionsMatch() {
        // Given
        when(repository.findExecutionIds(eq("main"), anyString(), eq(Op.EQUALS), any(), eq(ExecutionOutputFilterService.MAX_EXECUTIONS + 1)))
            .thenReturn(IntStream.rangeClosed(0, ExecutionOutputFilterService.MAX_EXECUTIONS).mapToObj(String::valueOf).toList());

        // When / Then
        assertThatThrownBy(() -> service.resolve("main", List.of(outputs(Op.EQUALS, Map.of("output", "value")))))
            .isInstanceOf(InvalidQueryFiltersException.class);
    }

    @Test
    void shouldFailWhenOperationIsNotSupported() {
        assertThatThrownBy(() -> service.resolve("main", List.of(outputs(Op.STARTS_WITH, Map.of("output", "value")))))
            .isInstanceOf(InvalidQueryFiltersException.class);
    }

    @Test
    void shouldFailWhenValueIsNull() {
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("output", null);

        assertThatThrownBy(() -> service.resolve("main", List.of(outputs(Op.EQUALS, nullValue))))
            .isInstanceOf(InvalidQueryFiltersException.class);
    }
}
