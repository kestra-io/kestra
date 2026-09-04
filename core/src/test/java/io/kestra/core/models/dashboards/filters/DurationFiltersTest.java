package io.kestra.core.models.dashboards.filters;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.InvalidQueryFiltersException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DurationFiltersTest {
    private static final Set<TestField> DURATION_FIELDS = Set.of(TestField.DURATION);

    enum TestField {
        DURATION,
        NAMESPACE
    }

    @Test
    void shouldConvertAnIso8601ValueToTheStoreUnit() {
        List<AbstractFilter<TestField>> filters = List.of(
            GreaterThan.<TestField> builder().field(TestField.DURATION).value("PT1.5S").build()
        );

        List<AbstractFilter<TestField>> normalized = DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis);

        assertThat(((GreaterThan<TestField>) normalized.getFirst()).getValue()).isEqualTo(1500L);
    }

    @Test
    void shouldReadAPlainNumberAsSeconds() {
        List<AbstractFilter<TestField>> filters = List.of(
            LessThanOrEqualTo.<TestField> builder().field(TestField.DURATION).value(2).build()
        );

        List<AbstractFilter<TestField>> normalized = DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis);

        assertThat(((LessThanOrEqualTo<TestField>) normalized.getFirst()).getValue()).isEqualTo(2000L);
    }

    @Test
    void shouldConvertEveryValueOfAnInFilter() {
        List<AbstractFilter<TestField>> filters = List.of(
            In.<TestField> builder().field(TestField.DURATION).values(List.of("PT1S", 2)).build()
        );

        List<AbstractFilter<TestField>> normalized = DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis);

        assertThat(((In<TestField>) normalized.getFirst()).getValues()).containsExactly(1000L, 2000L);
    }

    @Test
    void shouldConvertTheDurationFiltersNestedInAnOrFilter() {
        List<AbstractFilter<TestField>> filters = List.of(
            Or.<TestField> builder().values(
                List.of(
                    GreaterThan.<TestField> builder().field(TestField.DURATION).value("PT1S").build(),
                    EqualTo.<TestField> builder().field(TestField.NAMESPACE).value("company").build()
                )
            ).build()
        );

        List<AbstractFilter<TestField>> normalized = DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis);

        List<AbstractFilter<TestField>> nested = ((Or<TestField>) normalized.getFirst()).getValues();
        assertThat(((GreaterThan<TestField>) nested.getFirst()).getValue()).isEqualTo(1000L);
        assertThat(((EqualTo<TestField>) nested.getLast()).getValue()).isEqualTo("company");
    }

    @Test
    void shouldLeaveTheFiltersOnOtherFieldsUntouched() {
        List<AbstractFilter<TestField>> filters = List.of(
            GreaterThan.<TestField> builder().field(TestField.NAMESPACE).value("PT1S").build()
        );

        List<AbstractFilter<TestField>> normalized = DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis);

        assertThat(normalized.getFirst()).isSameAs(filters.getFirst());
    }

    @Test
    void shouldRejectAValueThatIsNotADuration() {
        List<AbstractFilter<TestField>> filters = List.of(
            GreaterThan.<TestField> builder().field(TestField.DURATION).value("1 second").build()
        );

        assertThatThrownBy(() -> DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("`1 second` is not a valid duration");
    }

    @Test
    void shouldRejectAFilterTypeThatCannotApplyToADuration() {
        List<AbstractFilter<TestField>> filters = List.of(
            StartsWith.<TestField> builder().field(TestField.DURATION).value("PT1S").build()
        );

        assertThatThrownBy(() -> DurationFilters.normalize(filters, DURATION_FIELDS, Duration::toMillis))
            .isInstanceOf(InvalidQueryFiltersException.class)
            .hasMessageContaining("filter type `STARTS_WITH` cannot be used on the duration field `DURATION`");
    }

    @Test
    void shouldReportOneViolationPerInvalidFilter() {
        List<AbstractFilter<TestField>> filters = List.of(
            GreaterThan.<TestField> builder().field(TestField.DURATION).value("PT1S").build(),
            LessThan.<TestField> builder().field(TestField.DURATION).value("nope").build(),
            StartsWith.<TestField> builder().field(TestField.DURATION).value("PT1S").build()
        );

        assertThat(DurationFilters.violations(filters, DURATION_FIELDS)).hasSize(2);
    }
}
