package io.kestra.webserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

import static io.kestra.webserver.utils.CSVUtils.toCSV;
import static org.assertj.core.api.Assertions.assertThat;

class CSVUtilsTest {
    @Test
    void ok_oneLine() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {
                {
                    put("one-header", "one-value");
                }
            }
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header\r\none-value\r\n");
    }

    @Test
    void ok_oneLine_number() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {
                {
                    put("one-header", 42);
                }
            }
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
            new LinkedHashMap<>() {
                {
                    put("one-header", instant);
                }
            }
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header\r\n%s\r\n".formatted(instant.toString()));
    }

    @Test
    void ok_oneLine_multipleValues() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {
                {
                    put("one-header", "one-value");
                    put("second-header", "second-value");
                }
            }
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("one-header,second-header\r\none-value,second-value\r\n");
    }

    @Test
    void ok_multipleLines_multipleValues() {
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {
                {
                    put("a-header", "a-value-1");
                    put("b-header", "b-value-1");
                }
            },
            new LinkedHashMap<>() {
                {
                    put("a-header", "a-value-2");
                    put("b-header", "b-value-2");
                }
            }
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();
        var outputStreamWriter = new OutputStreamWriter(byteArrayOutputStream);

        toCSV(outputStreamWriter, input);

        assertThat(byteArrayOutputStream.toString()).isEqualTo("a-header,b-header\r\na-value-1,b-value-1\r\na-value-2,b-value-2\r\n");
    }

    @Test
    void shouldAlignValuesWithHeaderWhenRecordsHaveDifferentKeys() {
        // Given
        List<Map<String, Object>> input = List.of(
            new LinkedHashMap<>() {
                {
                    put("a-header", "a-value-1");
                    put("b-header", "b-value-1");
                }
            },
            new LinkedHashMap<>() {
                {
                    put("a-header", "a-value-2");
                    put("extra-header", "extra-value-2");
                    put("b-header", "b-value-2");
                }
            }
        );

        var byteArrayOutputStream = new ByteArrayOutputStream();

        // When
        toCSV(new OutputStreamWriter(byteArrayOutputStream), input);

        // Then
        assertThat(byteArrayOutputStream.toString()).isEqualTo("a-header,b-header\r\na-value-1,b-value-1\r\na-value-2,b-value-2\r\n");
    }

    @Test
    void shouldEmitNothingWhenThereIsNoRecord() {
        // Given
        Flux<Map<String, Object>> input = Flux.empty();

        // When
        String csv = toCSVString(input);

        // Then
        assertThat(csv).isEmpty();
    }

    @Test
    void shouldEmitOneLinePerRecordPlusHeader() {
        // Given
        Flux<Map<String, Object>> input = Flux.range(1, 43)
            .map(i -> Map.of("id", (Object) ("execution-" + i)));

        // When
        String csv = toCSVString(input);

        // Then
        assertThat(csv.split("\r\n")).hasSize(44);
    }

    @Test
    void shouldQuoteValueWhenItContainsACarriageReturn() {
        // Given
        Flux<Map<String, Object>> input = Flux.just(
            Map.of("id", (Object) "execution-1"),
            Map.of("id", (Object) "execution\r-2"),
            Map.of("id", (Object) "execution-3")
        );

        // When
        String csv = toCSVString(input);

        // Then
        assertThat(csv).isEqualTo("id\r\nexecution-1\r\n\"execution\r-2\"\r\nexecution-3\r\n");
        assertThat(csv.split("\r\n")).hasSize(4);
    }

    @Test
    void shouldQuoteValueWhenItContainsASeparatorAQuoteOrALineFeed() {
        // Given
        Flux<Map<String, Object>> input = Flux.just(
            new LinkedHashMap<>() {
                {
                    put("separator", "a,b");
                    put("quote", "a\"b");
                    put("lineFeed", "a\nb");
                }
            }
        );

        // When
        String csv = toCSVString(input);

        // Then
        assertThat(csv).isEqualTo("separator,quote,lineFeed\r\n\"a,b\",\"a\"\"b\",\"a\nb\"\r\n");
    }

    @Test
    void shouldAlignStreamedValuesWithHeaderWhenRecordsHaveDifferentKeys() {
        // Given
        Flux<Map<String, Object>> input = Flux.just(
            new LinkedHashMap<>() {
                {
                    put("a-header", "a-value-1");
                    put("b-header", "b-value-1");
                }
            },
            new LinkedHashMap<>() {
                {
                    put("b-header", "b-value-2");
                }
            }
        );

        // When
        String csv = toCSVString(input);

        // Then
        assertThat(csv).isEqualTo("a-header,b-header\r\na-value-1,b-value-1\r\n,b-value-2\r\n");
    }

    private static String toCSVString(Flux<Map<String, Object>> records) {
        return String.join("", CSVUtils.toCSVFlux(records).collectList().block());
    }

    // TODO test in prod if missing data is actually a problem or not (next executions sometimes not having 'nextExec' field)
}