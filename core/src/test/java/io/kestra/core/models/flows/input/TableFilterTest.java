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
    void shouldLookAValueUpInAListCellOrAText() {
        assertThat(matches("tags", CONTAINS, "edge")).isTrue();
        assertThat(matches("tags", CONTAINS, List.of("db", "web"))).isTrue();
        assertThat(matches("tags", CONTAINS, "ed")).isFalse();
        assertThat(matches("type", CONTAINS, "nu")).isTrue();
    }
}
