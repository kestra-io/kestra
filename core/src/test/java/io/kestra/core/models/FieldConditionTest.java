package io.kestra.core.models;

import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.dashboards.filters.AbstractFilter.FilterType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldConditionTest {
    @Test
    void shouldTestEqualityOperators() {
        assertThat(new FieldCondition("f", FilterType.EQUAL_TO, "a").test("a")).isTrue();
        assertThat(new FieldCondition("f", FilterType.EQUAL_TO, "a").test("b")).isFalse();
        assertThat(new FieldCondition("f", FilterType.NOT_EQUAL_TO, "a").test("b")).isTrue();
        assertThat(new FieldCondition("f", FilterType.NOT_EQUAL_TO, "a").test("a")).isFalse();
    }

    @Test
    void shouldTestStringOperators() {
        assertThat(new FieldCondition("f", FilterType.STARTS_WITH, "io.kestra").test("io.kestra.plugin.gcp")).isTrue();
        assertThat(new FieldCondition("f", FilterType.ENDS_WITH, "Query").test("bigquery.Query")).isTrue();
        assertThat(new FieldCondition("f", FilterType.CONTAINS, "gcp").test("io.kestra.plugin.gcp.Query")).isTrue();
        assertThat(new FieldCondition("f", FilterType.REGEX, "prod\\..*").test("prod.data")).isTrue();
        assertThat(new FieldCondition("f", FilterType.REGEX, "prod\\..*").test("dev.data")).isFalse();
        assertThat(new FieldCondition("f", FilterType.STARTS_WITH, "io.kestra").test(null)).isFalse();
    }

    @Test
    void shouldTestCollectionOperators() {
        assertThat(new FieldCondition("f", FilterType.IN, List.of("a", "b")).test("a")).isTrue();
        assertThat(new FieldCondition("f", FilterType.IN, List.of("a", "b")).test("c")).isFalse();
        assertThat(new FieldCondition("f", FilterType.NOT_IN, List.of("a", "b")).test("c")).isTrue();
        assertThat(new FieldCondition("f", FilterType.NOT_IN, List.of("a", "b")).test("a")).isFalse();
    }

    @Test
    void shouldTestNullnessOperators() {
        assertThat(new FieldCondition("f", FilterType.IS_NULL, null).test(null)).isTrue();
        assertThat(new FieldCondition("f", FilterType.IS_NULL, null).test("x")).isFalse();
        assertThat(new FieldCondition("f", FilterType.IS_NOT_NULL, null).test("x")).isTrue();
        assertThat(new FieldCondition("f", FilterType.IS_NOT_NULL, null).test(null)).isFalse();
    }

    @Test
    void shouldCompareOnStringFormWhenValueIsNotString() {
        assertThat(new FieldCondition("f", FilterType.EQUAL_TO, "3").test(3)).isTrue();
        assertThat(new FieldCondition("f", FilterType.EQUAL_TO, true).test("true")).isTrue();
    }

    @Test
    void shouldKeepTheTextOperatorsCaseSensitive() {
        assertThat(new FieldCondition("f", FilterType.EQUAL_TO, "ACTIVE").test("Active")).isFalse();
        assertThat(new FieldCondition("f", FilterType.STARTS_WITH, "IO.kestra").test("io.kestra.plugin")).isFalse();
        assertThat(new FieldCondition("f", FilterType.REGEX, "^vm-web-\\d+$").test("vm-Web-01")).isFalse();
    }

    @Test
    void shouldMatchARegexAgainstTheWholeValue() {
        assertThat(new FieldCondition("f", FilterType.REGEX, "prod").test("prod.data")).isFalse();
    }

    @Test
    void shouldLetAnInvalidRegexFailRatherThanReadAsNoMatch() {
        assertThatThrownBy(() -> new FieldCondition("f", FilterType.REGEX, "(unclosed").test("prod.data"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void shouldMatchAPrefixAsAPlainStartOfText() {
        assertThat(new FieldCondition("f", FilterType.PREFIX, "prod").test("production")).isTrue();
        assertThat(new FieldCondition("f", FilterType.PREFIX, "prod").test("prod.eu")).isTrue();
    }

    @Test
    void shouldContainOnTheStringFormOfAList() {
        assertThat(new FieldCondition("f", FilterType.CONTAINS, "db").test(List.of("web", "db"))).isTrue();
        assertThat(new FieldCondition("f", FilterType.NOT_CONTAINS, "cache").test(List.of("web", "db"))).isTrue();
        assertThat(new FieldCondition("f", FilterType.NOT_CONTAINS, "db").test(null)).isTrue();
    }

    @Test
    void shouldNeverMatchAnOperatorThatDoesNotCompareAFieldToAValue() {
        assertThat(new FieldCondition("f", FilterType.OR, "a").test("a")).isFalse();
        assertThat(new FieldCondition("f", FilterType.IS_TRUE, null).test(true)).isFalse();
    }

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource(
        delimiter = '|', nullValues = "NULL", value = {
            "10                        | GREATER_THAN             | 9                         | true",
            "9                         | GREATER_THAN_OR_EQUAL_TO | 10                        | false",
            "apple                     | LESS_THAN                | banana                    | true",
            "2026-01-01T12:00:00+02:00 | LESS_THAN                | 2026-01-01T11:00:00Z      | true",
            "2026-03-01                | GREATER_THAN             | 2026-02-28T23:00:00Z      | true",
            "''                        | LESS_THAN                | 2026-01-01T00:00:00Z      | false",
            "''                        | LESS_THAN                | 5                         | false",
            "2026-01-01T10:00:00       | LESS_THAN                | 2026-01-02T10:00:00Z      | false",
            "NULL                      | GREATER_THAN             | 0                         | false",
        }
    )
    void shouldOrderNumbersAndDatesByValue(String actual, FilterType operator, String value, boolean expected) {
        assertThat(new FieldCondition("f", operator, value).test(actual)).isEqualTo(expected);
    }

    @Test
    void shouldOrderAnActualNumberAgainstItsTextForm() {
        assertThat(new FieldCondition("f", FilterType.GREATER_THAN, "3").test(4)).isTrue();
        assertThat(new FieldCondition("f", FilterType.LESS_THAN_OR_EQUAL_TO, 4).test("4.0")).isTrue();
    }
}
