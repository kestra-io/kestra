package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class H2FunctionsTest {
    @Test
    public void jqNull() {
        String jqString = H2Functions.jqString("{\"a\": null}", ".a");
        assertThat(jqString, is(nullValue()));
    }

    @Test
    public void jqString() {
        String jqString = H2Functions.jqString("{\"a\": \"b\"}", ".a");
        assertThat(jqString, is("b"));

        // on arrays, it will use the first element
        jqString = H2Functions.jqString("{\"labels\":[{\"key\":\"a\",\"value\":\"aValue\"},{\"key\":\"b\",\"value\":\"bValue\"}]}", ".labels[].value");
        assertThat(jqString, is("aValue"));
    }

    @Test
    public void jqStringWithArray() {
        String jqString = H2Functions.jqString("""
            {"a": [{"b": "c", "d": "e"}]}
            """, ".a[].b");
        assertThat(jqString, is("c"));
    }

    @Test
    public void jqBoolean() {
        Boolean jqString = H2Functions.jqBoolean("{\"a\": true}", ".a");
        assertThat(jqString, is(true));
    }

    @Test
    public void jqInteger() {
        Integer jqString = H2Functions.jqInteger("{\"a\": 2147483647}", ".a");
        assertThat(jqString, is(2147483647));
    }

    @Test
    public void jqLong() {
        Long jqString = H2Functions.jqLong("{\"a\": 9223372036854775807}", ".a");
        assertThat(jqString, is(9223372036854775807L));
    }

    @Test
    public void jqStringArray() {
        String[] jqString = H2Functions.jqStringArray("{\"a\": [\"1\", \"2\", \"3\"]}", ".a");
        assertThat(List.of(jqString), containsInAnyOrder("1", "2", "3"));
    }
}