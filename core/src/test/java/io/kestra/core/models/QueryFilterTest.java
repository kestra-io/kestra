package io.kestra.core.models;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.exceptions.InvalidQueryFiltersException;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.models.dashboards.filters.EqualTo;
import io.kestra.core.models.dashboards.filters.Prefix;
import io.kestra.core.models.dashboards.filters.StartsWith;

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
            () -> QueryFilter.validateQueryFilters(List.of(filter), resource)
        );
        assertThat(e.getMessage()).contains("Operation");
    }

    static Stream<Arguments> validOperationFilters() {
        return Stream.of(
            buildQueryFiltersForOperations(
                Field.QUERY, Resource.FLOW,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.SCOPE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.FLOW,
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

            buildQueryFiltersForOperations(
                Field.LABELS, Resource.FLOW,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN,
                    Op.CONTAINS
                )
            ),

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.START_DATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.END_DATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.STATE, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.IN,
                    Op.NOT_IN
                )
            ),

            buildQueryFiltersForOperations(
                Field.TRIGGER_EXECUTION_ID, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.TRIGGER_ID, Resource.LOG,
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

            buildQueryFiltersForOperations(
                Field.EXECUTION_ID, Resource.LOG,
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

            buildQueryFiltersForOperations(
                Field.CHILD_FILTER, Resource.EXECUTION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.WORKER_ID, Resource.TRIGGER,
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

            buildQueryFiltersForOperations(
                Field.EXISTING_ONLY, Resource.NAMESPACE,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.MIN_LEVEL, Resource.LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.CREATED, Resource.ASSET_USAGE,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.UPDATED, Resource.KV_METADATA,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.EXPIRATION_DATE, Resource.KV_METADATA,
                Set.of(
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN,
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.ID, Resource.ASSET,
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

            buildQueryFiltersForOperations(
                Field.ID, Resource.CREDENTIALS,
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

            buildQueryFiltersForOperations(
                Field.ASSET_ID, Resource.ASSET_USAGE,
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

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.ASSET,
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

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.CREDENTIALS,
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

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.PLUGIN,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAME, Resource.USER,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.USERNAME, Resource.USER,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.GROUP, Resource.USER,
                Set.of(
                    Op.EQUALS,
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(
                Field.EMAIL, Resource.INVITATION,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.STATUS, Resource.INVITATION,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.EXPIRED_AT, Resource.INVITATION,
                Set.of()
            ),

            buildQueryFiltersForOperations(
                Field.SUPER_ADMIN, Resource.INVITATION,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.ENABLED, Resource.SECURITY_INTEGRATION,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAME, Resource.ROLE,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAME, Resource.GROUP,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.BINDING,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )

            ),

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.BINDING,
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

            buildQueryFiltersForOperations(
                Field.TAGS, Resource.APP,
                Set.of(
                    Op.CONTAINS,
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.APP,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.APP,
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

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.APP,
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

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.TENANT,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.EXECUTION_ID, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.ID, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.USER_ID, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.RESOURCES, Resource.AUDIT_LOG,
                Set.of(
                    Op.IN
                )
            ),

            buildQueryFiltersForOperations(
                Field.DETAILS, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS
                )
            ),

            buildQueryFiltersForOperations(
                Field.START_DATE, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.END_DATE, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            )
        ).flatMap(s -> s);
    }

    @Test
    void toDashboardFilterBuilder_prefix_shouldReturnPrefixFilter() {
        QueryFilter filter = QueryFilter.builder()
            .field(Field.NAMESPACE)
            .operation(Op.PREFIX)
            .value("io.kestra.tests")
            .build();

        enum TestField {
            NAMESPACE
        }
        AbstractFilter<TestField> result = filter.toDashboardFilterBuilder(TestField.NAMESPACE, filter.value());

        assertThat(result).isInstanceOf(Prefix.class);
        Prefix<TestField> prefix = (Prefix<TestField>) result;
        assertThat(prefix.getValue()).isEqualTo("io.kestra.tests");
        assertThat(prefix.getField()).isEqualTo(TestField.NAMESPACE);
    }

    @Test
    void toDashboardFilterBuilder_equals_shouldReturnEqualToFilter() {
        QueryFilter filter = QueryFilter.builder()
            .field(Field.NAMESPACE)
            .operation(Op.EQUALS)
            .value("io.kestra.tests")
            .build();

        enum TestField {
            NAMESPACE
        }
        AbstractFilter<TestField> result = filter.toDashboardFilterBuilder(TestField.NAMESPACE, filter.value());

        assertThat(result).isInstanceOf(EqualTo.class);
    }

    @Test
    void toDashboardFilterBuilder_startsWith_shouldReturnStartsWithFilter() {
        QueryFilter filter = QueryFilter.builder()
            .field(Field.NAMESPACE)
            .operation(Op.STARTS_WITH)
            .value("io.kestra")
            .build();

        enum TestField {
            NAMESPACE
        }
        AbstractFilter<TestField> result = filter.toDashboardFilterBuilder(TestField.NAMESPACE, filter.value());

        assertThat(result).isInstanceOf(StartsWith.class);
    }

    static Stream<Arguments> invalidOperationFilters() {
        return Stream.of(
            buildQueryFiltersForOperations(
                Field.QUERY, Resource.FLOW,
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

            buildQueryFiltersForOperations(
                Field.SCOPE, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.FLOW,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.LABELS, Resource.FLOW,
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

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.START_DATE, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.END_DATE, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.STATE, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.TRIGGER_EXECUTION_ID, Resource.EXECUTION,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.TRIGGER_ID, Resource.LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.EXECUTION_ID, Resource.LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.CHILD_FILTER, Resource.EXECUTION,
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

            buildQueryFiltersForOperations(
                Field.WORKER_ID, Resource.TRIGGER,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.EXISTING_ONLY, Resource.NAMESPACE,
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

            buildQueryFiltersForOperations(
                Field.MIN_LEVEL, Resource.LOG,
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

            buildQueryFiltersForOperations(
                Field.CREATED, Resource.ASSET_USAGE,
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

            buildQueryFiltersForOperations(
                Field.UPDATED, Resource.KV_METADATA,
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

            buildQueryFiltersForOperations(
                Field.EXPIRATION_DATE, Resource.KV_METADATA,
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

            buildQueryFiltersForOperations(
                Field.ID, Resource.ASSET,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.ID, Resource.CREDENTIALS,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.ASSET_ID, Resource.ASSET_USAGE,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.ASSET,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.CREDENTIALS,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.PLUGIN,
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

            buildQueryFiltersForOperations(
                Field.NAME, Resource.USER,
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

            buildQueryFiltersForOperations(
                Field.USERNAME, Resource.USER,
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

            buildQueryFiltersForOperations(
                Field.GROUP, Resource.USER,
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

            buildQueryFiltersForOperations(
                Field.NAME, Resource.ROLE,
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

            buildQueryFiltersForOperations(
                Field.EMAIL, Resource.INVITATION,
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

            buildQueryFiltersForOperations(
                Field.STATUS, Resource.INVITATION,
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

            buildQueryFiltersForOperations(
                Field.EXPIRED_AT, Resource.INVITATION,
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

            buildQueryFiltersForOperations(
                Field.SUPER_ADMIN, Resource.INVITATION,
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

            buildQueryFiltersForOperations(
                Field.ENABLED, Resource.SECURITY_INTEGRATION,
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

            buildQueryFiltersForOperations(
                Field.NAME, Resource.GROUP,
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

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.BINDING,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )

            ),

            buildQueryFiltersForOperations(
                Field.TYPE, Resource.BINDING,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.TENANT,
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

            buildQueryFiltersForOperations(
                Field.TAGS, Resource.APP,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.APP,
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

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.APP,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.APP,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.QUERY, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.NAMESPACE, Resource.AUDIT_LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.FLOW_ID, Resource.AUDIT_LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.EXECUTION_ID, Resource.AUDIT_LOG,
                Set.of(
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.ID, Resource.AUDIT_LOG,
                Set.of(
                    Op.PREFIX,
                    Op.LESS_THAN,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.GREATER_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO
                )
            ),

            buildQueryFiltersForOperations(
                Field.USER_ID, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.RESOURCES, Resource.AUDIT_LOG,
                Set.of(
                    Op.EQUALS,
                    Op.NOT_EQUALS,
                    Op.GREATER_THAN,
                    Op.LESS_THAN,
                    Op.GREATER_THAN_OR_EQUAL_TO,
                    Op.LESS_THAN_OR_EQUAL_TO,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            ),

            buildQueryFiltersForOperations(
                Field.DETAILS, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.START_DATE, Resource.AUDIT_LOG,
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

            buildQueryFiltersForOperations(
                Field.END_DATE, Resource.AUDIT_LOG,
                Set.of(
                    Op.IN,
                    Op.NOT_IN,
                    Op.STARTS_WITH,
                    Op.ENDS_WITH,
                    Op.CONTAINS,
                    Op.REGEX,
                    Op.PREFIX
                )
            )
        ).flatMap(s -> s);
    }

    private static Stream<Arguments> buildQueryFiltersForOperations(Field field, Resource resource, Set<Op> operations) {
        return operations.stream().map(operation -> Arguments.of(QueryFilter.builder().field(field).operation(operation).build(), resource));
    }

}
