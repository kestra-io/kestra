package io.kestra.webserver.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.kestra.webserver.utils.CSVUtils.toCSV;
import static org.assertj.core.api.Assertions.assertThat;

class CSVUtilsTest {
    @Test
    void ok_oneLine() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {{
                put("one-header", "one-value");
            }}
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header\r\none-value\r\n");
    }

    @Test
    void ok_oneLine_number() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {{
                put("one-header", 42);
            }}
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header\r\n42\r\n");
    }

    @Test
    void ok_oneLine_date() {
        var instant = Instant.now();
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {{
                put("one-header", instant);
            }}
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header\r\n%s\r\n".formatted(instant.toString()));
    }

    @Test
    void ok_oneLine_multipleValues() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {{
                put("one-header", "one-value");
                put("second-header", "second-value");
            }}
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header,second-header\r\none-value,second-value\r\n");
    }

    @Test
    void ok_multipleLines_multipleValues() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {{
                put("a-header", "a-value-1");
                put("b-header", "b-value-1");
            }},
            new LinkedHashMap<>() {{
                put("a-header", "a-value-2");
                put("b-header", "b-value-2");
            }}
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("a-header,b-header\r\na-value-1,b-value-1\r\na-value-2,b-value-2\r\n");
    }

    // TODO test in prod if missing data is actually a problem or not (next executions sometimes not having 'nextExec' field)
}