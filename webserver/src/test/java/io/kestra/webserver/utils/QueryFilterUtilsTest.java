package io.kestra.webserver.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryFilterUtilsTest {

    private static QueryFilter leaf(QueryFilter.Field field, QueryFilter.Op op, Object value) {
        return QueryFilter.builder().field(field).operation(op).value(value).build();
    }

    @Test
    void resolveRelativeDate_pastOrientedField_resolvesToNowMinusDuration() {
        // Given — a relative duration on a past-oriented date field
        Duration range = Duration.ofHours(24);
        var filters = List.of(leaf(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO, range.toString()));

        // When
        ZonedDateTime before = ZonedDateTime.now();
        var result = QueryFilterUtils.resolveRelativeDateFilters(filters);
        ZonedDateTime after = ZonedDateTime.now();

        // Then — value is rewritten to an absolute instant ~ now - 24h, field/op preserved
        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
        ZonedDateTime resolved = ZonedDateTime.parse(result.get(0).value().toString());
        assertThat(resolved).isBetween(before.minus(range), after.minus(range));
    }

    @Test
    void resolveRelativeDate_futureOrientedField_resolvesToNowPlusDuration() {
        // Given — a relative duration on a future-oriented date field
        Duration range = Duration.ofDays(1);
        var filters = List.of(leaf(QueryFilter.Field.NEXT_EXECUTION_DATE, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO, range.toString()));

        // When
        ZonedDateTime before = ZonedDateTime.now();
        var result = QueryFilterUtils.resolveRelativeDateFilters(filters);
        ZonedDateTime after = ZonedDateTime.now();

        // Then — resolves forward from now
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.NEXT_EXECUTION_DATE);
        ZonedDateTime resolved = ZonedDateTime.parse(result.get(0).value().toString());
        assertThat(resolved).isBetween(before.plus(range), after.plus(range));
    }

    @Test
    void resolveRelativeDate_absoluteValue_isLeftUnchanged() {
        // Given — an absolute instant, not a duration
        String absolute = "2024-05-27T15:00:00+02:00";
        var filters = List.of(leaf(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO, absolute));

        // When
        var result = QueryFilterUtils.resolveRelativeDateFilters(filters);

        // Then — passes through untouched
        assertThat(result.get(0).value()).isEqualTo(absolute);
    }

    @Test
    void resolveRelativeDate_nonDateField_isLeftUnchanged() {
        // Given — a non-date field whose value happens to be duration-shaped
        var filters = List.of(leaf(QueryFilter.Field.NAMESPACE, QueryFilter.Op.EQUALS, "PT24H"));

        // When
        var result = QueryFilterUtils.resolveRelativeDateFilters(filters);

        // Then — untouched
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.NAMESPACE);
        assertThat(result.get(0).value()).isEqualTo("PT24H");
    }

    @Test
    void resolveRelativeDate_nestedInsideOrNode_preservesNodeAndRewritesLeaf() {
        // Given — a relative START_DATE nested inside an OR node alongside a LABELS leaf
        Duration range = Duration.ofHours(24);
        var dateLeaf = leaf(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO, range.toString());
        var labelsLeaf = leaf(QueryFilter.Field.LABELS, QueryFilter.Op.EQUALS, "foo:bar");
        var orNode = QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(List.of(dateLeaf, labelsLeaf))
            .build();

        // When
        var result = QueryFilterUtils.resolveRelativeDateFilters(List.of(orNode));

        // Then — the OR node is preserved; the nested date leaf is resolved
        assertThat(result).hasSize(1);
        var resultNode = result.get(0);
        assertThat(resultNode.isNode()).isTrue();
        assertThat(resultNode.logical()).isEqualTo(QueryFilter.Logical.OR);
        assertThat(resultNode.children()).hasSize(2);

        var rewrittenDate = resultNode.children().get(0);
        assertThat(rewrittenDate.field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(ZonedDateTime.parse(rewrittenDate.value().toString())).isBefore(ZonedDateTime.now());

        var preservedLabels = resultNode.children().get(1);
        assertThat(preservedLabels.field()).isEqualTo(QueryFilter.Field.LABELS);
        assertThat(preservedLabels.value()).isEqualTo("foo:bar");
    }

    @Test
    void resolveWithDefaultWindow_noDateFilter_appendsDefaultStartDate() {
        // Given — no date filter at all
        var filters = List.of(leaf(QueryFilter.Field.LABELS, QueryFilter.Op.EQUALS, "foo:bar"));

        // When
        var result = QueryFilterUtils.applyDefaultWindow(filters);

        // Then — a default START_DATE lower bound is appended
        assertThat(result).hasSize(2);
        var appended = result.get(1);
        assertThat(appended.field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(appended.operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
        assertThat(ZonedDateTime.parse(appended.value().toString())).isBefore(ZonedDateTime.now());
    }

    @Test
    void resolveWithDefaultWindow_boundedOnDate_appendsDefaultDateBound() {
        // Given — an event-like resource (logs) windowed on its single timestamp
        var filters = List.of(leaf(QueryFilter.Field.LABELS, QueryFilter.Op.EQUALS, "foo:bar"));

        // When
        var result = QueryFilterUtils.applyDefaultWindow(filters, QueryFilter.Field.DATE);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(1).field()).isEqualTo(QueryFilter.Field.DATE);
        assertThat(result.get(1).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void resolveWithDefaultWindow_upperBoundOnlyOnDate_isNotSupplementedWithAWindow() {
        // Given — only an upper bound on the log timestamp
        String absolute = "2024-05-27T15:00:00+02:00";
        var filters = List.of(leaf(QueryFilter.Field.DATE, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO, absolute));

        // When
        var result = QueryFilterUtils.applyDefaultWindow(filters, QueryFilter.Field.DATE);

        // Then — the caller's bound is left alone rather than being ANDed with an unrelated -8d window
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo(absolute);
    }

    @Test
    void resolveWithDefaultWindow_existingStartDate_isNotOverridden() {
        // Given — an explicit absolute start date already present
        String absolute = "2024-05-27T15:00:00+02:00";
        var filters = List.of(leaf(QueryFilter.Field.START_DATE, QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO, absolute));

        // When
        var result = QueryFilterUtils.applyDefaultWindow(filters);

        // Then — no default appended, the existing filter is kept as-is
        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualTo(absolute);
    }

    @Test
    void rewriteTriggerDateFilters_remapsStartDateToTriggerFieldPreservingValue() {
        // Given — a legacy generic startDate filter targeting the next-execution column. Relative durations
        // are resolved upstream by the binder, so this remap only renames the field and preserves the value.
        String absolute = "2024-05-27T15:00:00+02:00";
        var filters = List.of(leaf(QueryFilter.Field.START_DATE, QueryFilter.Op.LESS_THAN_OR_EQUAL_TO, absolute));

        // When
        var result = QueryFilterUtils.rewriteTriggerDateFilters(filters, QueryFilter.Field.NEXT_EXECUTION_DATE);

        // Then — remapped to NEXT_EXECUTION_DATE, operation and value untouched
        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.NEXT_EXECUTION_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO);
        assertThat(result.get(0).value()).isEqualTo(absolute);
    }
}
