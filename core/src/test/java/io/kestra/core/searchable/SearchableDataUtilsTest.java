package io.kestra.core.searchable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Data;
import io.kestra.core.models.flows.Output;
import io.kestra.core.models.flows.Type;

import static io.kestra.core.searchable.SearchableDataUtils.MAX_SEARCHABLE_ARRAY_ITEMS;
import static io.kestra.core.searchable.SearchableDataUtils.MAX_SEARCHABLE_VALUE_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchableDataUtilsTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchableDataUtilsTest.class);

    private static final Execution EXECUTION = Execution.builder().id("execution").tenantId("tenant").namespace("io.kestra.test").flowId("flow").build();

    private static Data data(Type type) {
        return Output.builder().id("data").type(type).value("{{ x }}").searchable(true).build();
    }

    private static List<SearchableData> toSearchableData(Data data, Object value) {
        return SearchableDataUtils.toSearchableData(LOGGER, EXECUTION, data, value);
    }

    @Test
    void shouldReturnASingleValueAtIndexZeroForAScalar() {
        assertThat(toSearchableData(data(Type.INT), 42)).containsExactly(new SearchableData("tenant", "execution", "data", 0, "42"));
    }

    @Test
    void shouldReturnOneValuePerItemForAnArray() {
        assertThat(toSearchableData(data(Type.ARRAY), List.of("a", "b", "c"))).containsExactly(
            new SearchableData("tenant", "execution", "data", 0, "a"),
            new SearchableData("tenant", "execution", "data", 1, "b"),
            new SearchableData("tenant", "execution", "data", 2, "c")
        );
    }

    @Test
    void shouldSkipNullItemsOfAnArray() {
        assertThat(toSearchableData(data(Type.ARRAY), Arrays.asList("a", null, "b"))).containsExactly(
            new SearchableData("tenant", "execution", "data", 0, "a"),
            new SearchableData("tenant", "execution", "data", 1, "b")
        );
    }

    @Test
    void shouldReturnNothingForAnAbsentValueOrAnEmptyArray() {
        assertThat(toSearchableData(data(Type.STRING), null)).isEmpty();
        assertThat(toSearchableData(data(Type.ARRAY), List.of())).isEmpty();
    }

    @Test
    void shouldRejectAnArrayDataWhoseValueIsNotAList() {
        assertThatThrownBy(() -> toSearchableData(data(Type.ARRAY), "a"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("is not a list");
    }

    @Test
    void shouldRejectANonScalarArrayItem() {
        assertThatThrownBy(() -> toSearchableData(data(Type.ARRAY), List.of(List.of("a"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Only scalar items");
    }

    @Test
    void shouldRejectADataOfAnUnsupportedType() {
        assertThatThrownBy(() -> toSearchableData(data(Type.JSON), "{}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("type JSON is not supported");
    }

    @Test
    void shouldTruncateAValueLongerThanTheLimit() {
        List<SearchableData> result = toSearchableData(data(Type.STRING), "a".repeat(MAX_SEARCHABLE_VALUE_LENGTH + 10));

        assertThat(result).singleElement().satisfies(it -> assertThat(it.value()).hasSize(MAX_SEARCHABLE_VALUE_LENGTH));
    }

    @Test
    void shouldKeepAValueOfExactlyTheLimit() {
        String value = "a".repeat(MAX_SEARCHABLE_VALUE_LENGTH);

        assertThat(toSearchableData(data(Type.STRING), value)).singleElement().satisfies(it -> assertThat(it.value()).isEqualTo(value));
    }

    @Test
    void shouldNotCutASurrogatePairWhenTruncating() {
        String value = "a".repeat(MAX_SEARCHABLE_VALUE_LENGTH - 1) + "😀" + "b";

        assertThat(toSearchableData(data(Type.STRING), value)).singleElement()
            .satisfies(it -> assertThat(it.value()).isEqualTo("a".repeat(MAX_SEARCHABLE_VALUE_LENGTH - 1)));
    }

    @Test
    void shouldTruncateTheValuesOfAnArray() {
        List<SearchableData> result = toSearchableData(data(Type.ARRAY), List.of("a".repeat(MAX_SEARCHABLE_VALUE_LENGTH + 10), "b"));

        assertThat(result).extracting(it -> it.value().length()).containsExactly(MAX_SEARCHABLE_VALUE_LENGTH, 1);
    }

    @Test
    void shouldKeepOnlyTheFirstItemsOfAnArrayLongerThanTheLimit() {
        List<String> items = IntStream.range(0, MAX_SEARCHABLE_ARRAY_ITEMS + 5).mapToObj(i -> "ORD-" + i).toList();

        List<SearchableData> result = toSearchableData(data(Type.ARRAY), items);

        assertThat(result).hasSize(MAX_SEARCHABLE_ARRAY_ITEMS);
        assertThat(result.getLast().value()).isEqualTo("ORD-" + (MAX_SEARCHABLE_ARRAY_ITEMS - 1));
    }
}
