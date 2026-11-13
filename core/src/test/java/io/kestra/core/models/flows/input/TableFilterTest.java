package io.kestra.core.models.flows.input;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static io.kestra.core.models.flows.input.TableFilter.Operation.*;
import static org.assertj.core.api.Assertions.assertThat;

class TableFilterTest {
    private static final Map<String, Object> ROW = Map.of("type", "linux", "cpus", 4, "tags", List.of("web", "edge"));

    private static boolean matches(String column, TableFilter.Operation operation, Object value) {
        return new TableFilter(column, operation, value).matches(ROW);
    }

    @Test
    void shouldCompareACellAsTextWhenCheckingEquality() {
        assertThat(matches("cpus", EQUALS, "4")).isTrue();
        assertThat(matches("type", EQUALS, "windows")).isFalse();
        assertThat(matches("type", NOT_EQUALS, "windows")).isTrue();
    }

    @Test
    void shouldLookACellUpInAList() {
        assertThat(matches("type", IN, List.of("linux", "bsd"))).isTrue();
        assertThat(matches("type", IN, List.of("windows"))).isFalse();
        assertThat(matches("type", NOT_IN, List.of("windows"))).isTrue();
        assertThat(matches("missing", NOT_IN, List.of("windows"))).isTrue();
    }

    @Test
    void shouldCompareNumbersAsNumbers() {
        Map<String, Object> row = Map.of("ratio", 2, "code", "007");

        assertThat(new TableFilter("ratio", EQUALS, 2.0d).matches(row)).isTrue();
        assertThat(new TableFilter("ratio", IN, List.of(1.5d, 2.0d)).matches(row)).isTrue();
        assertThat(new TableFilter("ratio", NOT_EQUALS, "2.0").matches(row)).isFalse();
        assertThat(new TableFilter("code", EQUALS, "7").matches(row)).isFalse();
    }

    @Test
    void shouldTreatAListUnderEqualsAsOneOfItsValues() {
        assertThat(matches("type", EQUALS, List.of("linux"))).isTrue();
        assertThat(matches("type", NOT_EQUALS, List.of("linux", "bsd"))).isFalse();
    }

    @Test
    void shouldTreatAValueRenderedToNothingAsAnEmptyList() {
        Map<String, Object> row = Map.of("type", "null", "name", "annulled");

        assertThat(new TableFilter("type", EQUALS, null).matches(row)).isFalse();
        assertThat(new TableFilter("type", IN, null).matches(row)).isFalse();
        assertThat(new TableFilter("name", CONTAINS, null).matches(row)).isFalse();
        assertThat(new TableFilter("type", NOT_IN, null).matches(row)).isTrue();
    }

    @Test
    void shouldLookAValueUpInAListCellOrAText() {
        assertThat(matches("tags", CONTAINS, "edge")).isTrue();
        assertThat(matches("tags", CONTAINS, List.of("db", "web"))).isTrue();
        assertThat(matches("tags", CONTAINS, "ed")).isFalse();
        assertThat(matches("type", CONTAINS, "nu")).isTrue();
    }
}
