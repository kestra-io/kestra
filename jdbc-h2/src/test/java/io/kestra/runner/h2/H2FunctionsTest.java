package io.kestra.runner.h2;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

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
}