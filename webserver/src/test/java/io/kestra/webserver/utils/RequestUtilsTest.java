package io.kestra.webserver.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

class RequestUtilsTest {
    @Test
    void extractLabels() {
        final Map<String, String> inputParam = Map.of(
            "label-1", "labelA", "test", "testValue", "label-2", "labelB"
        );

        final Map<String, String> extracted = RequestUtils.extractLabels(inputParam);

        assertThat(extracted.size(), is(2));
        assertThat(extracted.get("1"), is("labelA"));
        assertThat(extracted.get("2"), is("labelB"));
    }

    @Test
    void extractLabelsNullCheck() {
        final Map<String, String> extracted = RequestUtils.extractLabels(null);

        assertThat(extracted, is(nullValue()));
    }

    @Test
    void extractInputs() {
        final Map<String, String> inputParam = Map.of(
            "label-1", "labelA", "test", "testValue", "label-2", "labelB"
        );

        final Map<String, String> extracted = RequestUtils.extractInputs(inputParam);

        assertThat(extracted.size(), is(1));
        assertThat(extracted.get("test"), is("testValue"));
    }

    @Test
    void extractInputsNullCheck() {
        final Map<String, String> extracted = RequestUtils.extractInputs(null);

        assertThat(extracted, is(nullValue()));
    }
}