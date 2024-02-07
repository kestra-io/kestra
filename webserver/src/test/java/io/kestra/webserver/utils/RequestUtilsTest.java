package io.kestra.webserver.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class RequestUtilsTest {

    @Test
    void toMap() {
        final Map<String, String> resultMap = RequestUtils.toMap(List.of("timestamp:2023-12-18T14:32:14Z"));

        assertThat(resultMap.get("timestamp"), is("2023-12-18T14:32:14Z"));
    }
}