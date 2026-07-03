package io.kestra.webserver.utils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ExecutionRepositoryInterface.DateFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class QueryFilterUtilsTest {
    ZonedDateTime date = ZonedDateTime.parse("2024-05-27T15:00:00+02:00[Europe/Paris]");

    @Test
    void replaceTimeRange_startDateMode_producesStartDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_endDateMode_producesEndDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.END_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.END_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_startOrEndDateMode_producesStartDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_OR_END_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_nestedInsideOrNode_startDateMode_preservesNodeAndRewritesLeaf() {
        // Given — TIME_RANGE nested inside an OR node alongside a LABELS leaf (the exact shape the
        // frontend produces when the user adds a conditional group on the Executions page)
        var timeRangeLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.TIME_RANGE)
            .operation(QueryFilter.Op.EQUALS)
            .value("PT24H")
            .build();
        var labelsLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value("foo:bar")
            .build();
        var orNode = QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(List.of(timeRangeLeaf, labelsLeaf))
            .build();
        var filters = List.of(orNode);

        // When
        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_DATE);

        // Then — the OR node is preserved; the TIME_RANGE leaf is rewritten to START_DATE
        assertThat(result).hasSize(1);
        var resultNode = result.get(0);
        assertThat(resultNode.isNode()).isTrue();
        assertThat(resultNode.logical()).isEqualTo(QueryFilter.Logical.OR);
        assertThat(resultNode.children()).hasSize(2);

        var rewrittenDate = resultNode.children().get(0);
        assertThat(rewrittenDate.field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(rewrittenDate.operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);

        var preservedLabels = resultNode.children().get(1);
        assertThat(preservedLabels.field()).isEqualTo(QueryFilter.Field.LABELS);
        assertThat(preservedLabels.operation()).isEqualTo(QueryFilter.Op.EQUALS);
    }

    @Test
    void replaceTimeRange_nestedInsideOrNode_endDateMode_rewritesLeafToEndDate() {
        // Given
        var timeRangeLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.TIME_RANGE)
            .operation(QueryFilter.Op.EQUALS)
            .value("PT24H")
            .build();
        var labelsLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value("foo:bar")
            .build();
        var orNode = QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(List.of(timeRangeLeaf, labelsLeaf))
            .build();
        var filters = List.of(orNode);

        // When
        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.END_DATE);

        // Then — nested leaf is rewritten to END_DATE
        assertThat(result).hasSize(1);
        var resultNode = result.get(0);
        assertThat(resultNode.isNode()).isTrue();
        assertThat(resultNode.children().get(0).field()).isEqualTo(QueryFilter.Field.END_DATE);
        assertThat(resultNode.children().get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void updateFilters_flatListWithNoDateFilter_appendsDefaultStartDate() {
        // Given — flat list with no date filter at all
        var labelsLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value("foo:bar")
            .build();
        var filters = List.of(labelsLeaf);
        var resolvedStartDate = ZonedDateTime.now().minusDays(8);

        // When
        var result = QueryFilterUtils.updateFilters(filters, resolvedStartDate);

        // Then — default START_DATE is appended at top level
        assertThat(result).hasSize(2);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.LABELS);
        var appended = result.get(1);
        assertThat(appended.field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(appended.operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    private static List<QueryFilter> timeRangeFilter() {
        return List.of(
            QueryFilter.builder()
                .field(QueryFilter.Field.TIME_RANGE)
                .operation(QueryFilter.Op.EQUALS)
                .value("PT24H")
                .build()
        );
    }
}
