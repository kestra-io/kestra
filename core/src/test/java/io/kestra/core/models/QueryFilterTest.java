package io.kestra.core.models;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.QueryFilter.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class QueryFilterTest {

    @ParameterizedTest
    @MethodSource("validOperationFilters")
    void should_validate_all_operations(QueryFilter filter, Resource resource) {
        assertDoesNotThrow(() -> QueryFilter.validateQueryFilters(List.of(filter), resource));
    }

    @ParameterizedTest
    @MethodSource("invalidOperationFilters")
    void should_fail_to_validate_all_operations(QueryFilter filter, Resource resource) {
        InvalidQueryFiltersException e = assertThrows(
            InvalidQueryFiltersException.class,
            () -> QueryFilter.validateQueryFilters(List.of(filter), resource));
        assertThat(e.getMessage()).contains("Operation");
    }

    static Stream<Arguments> validOperationFilters() {
        return Stream.of(
            buildQueryFiltersForOperations(Field.QUERY, Resource.FLOW,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.SCOPE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.NAMESPACE, Resource.FLOW,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.LABELS, Resource.FLOW,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(Field.FLOW_ID, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.START_DATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.END_DATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.STATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.TRIGGER_EXECUTION_ID, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(Field.TRIGGER_ID, Resource.LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(Field.EXECUTION_ID, Resource.LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(Field.CHILD_FILTER, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.WORKER_ID, Resource.TRIGGER,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(Field.EXISTING_ONLY, Resource.NAMESPACE,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.MIN_LEVEL, Resource.LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.CREATED, Resource.ASSET_USAGE,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.UPDATED, Resource.KV_METADATA,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.EXPIRATION_DATE, Resource.KV_METADATA,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.ID, Resource.ASSET,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.ID, Resource.CREDENTIALS,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.ASSET_ID, Resource.ASSET_USAGE,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.ASSET,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.CREDENTIALS,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.USER,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.USERNAME, Resource.USER,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.GROUP, Resource.USER,
                Set.of(
                    Op.EQUALS,
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(Field.EMAIL, Resource.INVITATION,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.STATUS, Resource.INVITATION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.EXPIRED_AT, Resource.INVITATION,
                Set.of()
            ),

            buildQueryFiltersForOperations(Field.ENABLED, Resource.SECURITY_INTEGRATION,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.ROLE,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.GROUP,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.NAMESPACE, Resource.BINDING,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )

            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.BINDING,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.CONTAINS,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.IN,
                    Op.NOT_IN
                )
            )
        ).flatMap(s -> s);
    }

    static Stream<Arguments> invalidOperationFilters() {
        return Stream.of(
            buildQueryFiltersForOperations(Field.QUERY, Resource.FLOW,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.SCOPE, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.NAMESPACE, Resource.FLOW,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),


            buildQueryFiltersForOperations(Field.LABELS, Resource.FLOW,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.FLOW_ID, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.START_DATE, Resource.EXECUTION,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.END_DATE, Resource.EXECUTION,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.STATE, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.TRIGGER_EXECUTION_ID, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.TRIGGER_ID, Resource.LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.EXECUTION_ID, Resource.LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.CHILD_FILTER, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.WORKER_ID, Resource.TRIGGER,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.EXISTING_ONLY, Resource.NAMESPACE,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.MIN_LEVEL, Resource.LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.CREATED, Resource.ASSET_USAGE,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),


            buildQueryFiltersForOperations(Field.UPDATED, Resource.KV_METADATA,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.EXPIRATION_DATE, Resource.KV_METADATA,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.ID, Resource.ASSET,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.ID, Resource.CREDENTIALS,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.ASSET_ID, Resource.ASSET_USAGE,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.ASSET,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.CREDENTIALS,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.USER,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.USERNAME, Resource.USER,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.GROUP, Resource.USER,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.CONTAINS,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.PREFIX,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.ROLE,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.CONTAINS,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.PREFIX,
                    Op.NOT_EQUALS,
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(Field.EMAIL, Resource.INVITATION,
                Set.of(
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.STATUS, Resource.INVITATION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.EXPIRED_AT, Resource.INVITATION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.ENABLED, Resource.SECURITY_INTEGRATION,
                Set.of(
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(Field.NAME, Resource.GROUP,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.CONTAINS,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.PREFIX,
                    Op.NOT_EQUALS,
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(Field.NAMESPACE, Resource.BINDING,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )

            ),

            buildQueryFiltersForOperations(Field.TYPE, Resource.BINDING,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            )
        ).flatMap(s -> s);
    }

    private static Stream<Arguments> buildQueryFiltersForOperations(Field field, Resource resource, Set<Op> operations) {
        return operations.stream().map(operation -> Arguments.of(QueryFilter.builder().field(field).operation(operation).build(), resource));
    }

}
