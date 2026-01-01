package io.kestra.core.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.QueryFilter.Resource;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class QueryFilterTest {

    @ParameterizedTest
    @MethodSource("validOperationFilters")
    void should_validate_all_operations(QueryFilter filter, Resource resource){
        assertDoesNotThrow(() -> QueryFilter.validateQueryFilters(List.of(filter), resource));
    }

    @ParameterizedTest
    @MethodSource("invalidOperationFilters")
    void should_fail_to_validate_all_operations(QueryFilter filter, Resource resource){
        InvalidQueryFiltersException e = assertThrows(
            InvalidQueryFiltersException.class,
            () -> QueryFilter.validateQueryFilters(List.of(filter), resource));
        assertThat(e.getMessage()).contains("Operation");
    }

    static Stream<Arguments> validOperationFilters() {
        return Stream.of(
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.EQUALS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.NOT_EQUALS).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.EQUALS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.NOT_EQUALS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.NOT_IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.STARTS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.ENDS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.CONTAINS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.REGEX).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.PREFIX).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.EQUALS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.NOT_EQUALS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.NOT_IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.CONTAINS).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.NOT_IN).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.CONTAINS).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.EQUALS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.NOT_EQUALS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.NOT_IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.STARTS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.ENDS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.CONTAINS).build(), Resource.LOG),

            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.EQUALS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.NOT_EQUALS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.NOT_IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.STARTS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.ENDS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.CONTAINS).build(), Resource.LOG),

            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.EQUALS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.NOT_EQUALS).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.EQUALS).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.NOT_EQUALS).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.IN).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.NOT_IN).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.STARTS_WITH).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.ENDS_WITH).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.CONTAINS).build(), Resource.TRIGGER),

            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.EQUALS).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.NOT_EQUALS).build(), Resource.NAMESPACE),

            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.EQUALS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.NOT_EQUALS).build(), Resource.LOG)
        );
    }

    static Stream<Arguments> invalidOperationFilters() {
        return Stream.of(
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.GREATER_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.LESS_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.NOT_IN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.STARTS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.ENDS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.CONTAINS).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.REGEX).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.QUERY).operation(Op.PREFIX).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.SCOPE).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.GREATER_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.LESS_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.NAMESPACE).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.GREATER_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.LESS_THAN).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.STARTS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.ENDS_WITH).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.REGEX).build(), Resource.FLOW),
            Arguments.of(QueryFilter.builder().field(Field.LABELS).operation(Op.PREFIX).build(), Resource.FLOW),

            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.FLOW_ID).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.START_DATE).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.END_DATE).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.STATE).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_EXECUTION_ID).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.GREATER_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.LESS_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.REGEX).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.TRIGGER_ID).operation(Op.PREFIX).build(), Resource.LOG),

            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.GREATER_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.LESS_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.REGEX).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.EXECUTION_ID).operation(Op.PREFIX).build(), Resource.LOG),

            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.GREATER_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.LESS_THAN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.NOT_IN).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.STARTS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.ENDS_WITH).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.CONTAINS).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.REGEX).build(), Resource.EXECUTION),
            Arguments.of(QueryFilter.builder().field(Field.CHILD_FILTER).operation(Op.PREFIX).build(), Resource.EXECUTION),

            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.GREATER_THAN).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.LESS_THAN).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.REGEX).build(), Resource.TRIGGER),
            Arguments.of(QueryFilter.builder().field(Field.WORKER_ID).operation(Op.PREFIX).build(), Resource.TRIGGER),

            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.GREATER_THAN).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.LESS_THAN).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.IN).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.NOT_IN).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.STARTS_WITH).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.ENDS_WITH).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.CONTAINS).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.REGEX).build(), Resource.NAMESPACE),
            Arguments.of(QueryFilter.builder().field(Field.EXISTING_ONLY).operation(Op.PREFIX).build(), Resource.NAMESPACE),

            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.GREATER_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.LESS_THAN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.GREATER_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.LESS_THAN_OR_EQUAL_TO).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.NOT_IN).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.STARTS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.ENDS_WITH).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.CONTAINS).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.REGEX).build(), Resource.LOG),
            Arguments.of(QueryFilter.builder().field(Field.MIN_LEVEL).operation(Op.PREFIX).build(), Resource.LOG)
        );
    }

}
