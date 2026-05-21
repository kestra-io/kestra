package io.kestra.core.serializers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.core.type.TypeReference;

import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;

class FileSerdeTest {
    static Stream<Arguments> source() {
        return Stream.of(
            Arguments.of("hello", null),
            Arguments.of(1, null),
            Arguments.of(1F, 1.D),
            Arguments.of(1.25D, null),
            Arguments.of(LocalDate.parse("2008-12-25"), null),
            Arguments.of(Date.from(Instant.parse("2008-12-25T15:30:00.123Z")), Instant.parse("2008-12-25T15:30:00.123Z")),
            Arguments.of(LocalDateTime.parse("2008-12-25T15:30:00.123"), null),
            Arguments.of(ZonedDateTime.parse("2008-12-25T15:30:00.123+01:00"), null),
            Arguments.of(ZonedDateTime.parse("2008-12-25T15:30:00.123+01:00").toOffsetDateTime(), null),
            Arguments.of(LocalTime.parse("15:30:00.123456"), null),
            Arguments.of(Instant.parse("2008-12-25T15:30:00.123Z"), null),
            Arguments.of(ZonedDateTime.parse("2008-12-25T15:30:00.123+01:00"), null),
            Arguments.of(Arrays.asList(1.1D, 2.2D, 3.3D), null),
            Arguments.of(Map.of("x", 4.1D, "y", 0.1D, "z", 3.1D), null)
        );
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "deprecated" })
    @ParameterizedTest
    @MethodSource("source")
    void ion(Object value, Object resultValue) throws IOException {
        Map<String, Object> object = new HashMap<>();
        object.put("key", value);

        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, object);
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            Map<String, Object> result = FileSerde.readAll(inputStream)
                .map(o -> (Map<String, Object>) o)
                .blockFirst();

            if (value instanceof Map) {
                assertThat(((Map) object.get("key")).entrySet(), everyItem(in(((Map) result.get("key")).entrySet())));
                assertThat(((Map) result.get("key")).entrySet(), everyItem(in(((Map) object.get("key")).entrySet())));
            } else if (value instanceof Collections) {
                assertThat((List) object.get("key")).containsExactlyInAnyOrder((List) result.get("key"));
            } else {
                assertThat(result.get("key")).isEqualTo(resultValue != null ? resultValue : object.get("key"));
            }
        }
    }

    @Test
    void readMax() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, Map.of("key1", "value1"));
            FileSerde.write(outputStream, Map.of("key2", "value2"));
            FileSerde.write(outputStream, Map.of("key3", "value3"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            FileSerde.read(inputStream, 2, row -> list.add(row));

            assertThat(list.size()).isEqualTo(2);
        }
    }

    @Test
    void readAll_fromEmptySource() throws IOException {
        final Path inputTempFilePath = createTempFile();

        final List<Object> outputValues = FileSerde.readAll(Files.newBufferedReader(inputTempFilePath)).collectList().block();
        assertThat(outputValues).isEmpty();
    }

    @Test
    void readAll_fromSingleValuedSource() throws IOException {
        final Path inputTempFilePath = createTempFile();

        final List<String> inputLines = List.of("{id:1,value:\"value1\"}");
        Files.write(inputTempFilePath, inputLines);

        final List<SimpleEntry> outputValues = FileSerde.readAll(Files.newBufferedReader(inputTempFilePath), new TypeReference<SimpleEntry>() {
        }).collectList().block();
        assertThat(outputValues).hasSize(1);
        assertThat(outputValues.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
    }

    @Test
    void readAll_fromMultiValuedSource() throws IOException {
        final Path inputTempFilePath = createTempFile();

        final List<String> inputLines = List.of("{id:1,value:\"value1\"}", "{id:2,value:\"value2\"}", "{id:3,value:\"value3\"}");
        Files.write(inputTempFilePath, inputLines);

        final List<SimpleEntry> outputValues = FileSerde.readAll(Files.newBufferedReader(inputTempFilePath), new TypeReference<SimpleEntry>() {
        }).collectList().block();
        assertThat(outputValues).hasSize(3);
        assertThat(outputValues.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
        assertThat(outputValues.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
        assertThat(outputValues.get(2)).isEqualTo(new SimpleEntry(3, "value3"));
    }

    @Test
    void writeAll_fromEmptySource() throws IOException {
        final Path outputTempFilePath = createTempFile();

        final Long outputCount = FileSerde.writeAll(Files.newBufferedWriter(outputTempFilePath), Flux.empty()).block();
        assertThat(outputCount).isEqualTo(0L);
    }

    @Test
    void writeAll_fromSingleValuedSource() throws IOException {
        final Path outputTempFilePath = createTempFile();

        final List<SimpleEntry> inputValues = List.of(new SimpleEntry(1, "value1"));
        final Long outputCount = FileSerde.writeAll(Files.newBufferedWriter(outputTempFilePath), Flux.fromIterable(inputValues)).block();
        assertThat(outputCount).isEqualTo(1L);

        final List<String> outputLines = Files.readAllLines(outputTempFilePath);
        assertThat(outputLines).hasSize(1);
        assertThat(outputLines.getFirst()).isEqualTo("{id:1,value:\"value1\"}");
    }

    @Test
    void writeAll_fromMultiValuedSource() throws IOException {
        final Path outputTempFilePath = createTempFile();

        final List<SimpleEntry> inputValues = List.of(new SimpleEntry(1, "value1"), new SimpleEntry(2, "value2"), new SimpleEntry(3, "value3"));
        final Long outputCount = FileSerde.writeAll(Files.newBufferedWriter(outputTempFilePath), Flux.fromIterable(inputValues)).block();
        assertThat(outputCount).isEqualTo(3L);

        final List<String> outputLines = Files.readAllLines(outputTempFilePath);
        assertThat(outputLines).hasSize(3);
        assertThat(outputLines.getFirst()).isEqualTo("{id:1,value:\"value1\"}");
        assertThat(outputLines.get(1)).isEqualTo("{id:2,value:\"value2\"}");
        assertThat(outputLines.get(2)).isEqualTo("{id:3,value:\"value3\"}");
    }

    @Test
    void writeAll_fromReadAll() throws IOException {
        final Path inputTempFilePath = createTempFile();
        final Path outputTempFilePath = createTempFile();

        final List<String> inputLines = List.of("{id:1,value:\"value1\"}", "{id:2,value:\"value2\"}", "{id:3,value:\"value3\"}");
        Files.write(inputTempFilePath, inputLines);

        final Flux<Object> inputFlux = FileSerde.readAll(Files.newBufferedReader(inputTempFilePath));
        final Long outputCount = FileSerde.writeAll(Files.newBufferedWriter(outputTempFilePath), inputFlux).block();
        assertThat(outputCount).isEqualTo(3L);

        final List<String> outputLines = Files.readAllLines(outputTempFilePath);
        assertThat(outputLines).isEqualTo(inputLines);
    }

    @SuppressWarnings("unchecked")
    @Test
    void binaryRoundtripMultiValue() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            FileSerde.write(out, Map.of("id", 1, "name", "alice"));
            FileSerde.write(out, Map.of("id", 2, "name", "bob"));
            FileSerde.write(out, Map.of("id", 3, "name", "charlie"));
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<Map<String, Object>> results = FileSerde.readAll(in)
                .map(o -> (Map<String, Object>) o)
                .collectList()
                .block();

            assertThat(results).hasSize(3);
            assertThat(results.get(0).get("id")).isEqualTo(1);
            assertThat(results.get(1).get("id")).isEqualTo(2);
            assertThat(results.get(2).get("id")).isEqualTo(3);
        }
    }

    @Test
    void readAllBinaryFromEmptyStream() throws IOException {
        List<Object> result = FileSerde.readAll(new ByteArrayInputStream(new byte[0]))
            .collectList()
            .block();
        assertThat(result).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void readTextIonViaInputStream() throws IOException {
        // Write text ION manually (simulating old 1.x format)
        Path tempFile = createTempFile();
        List<String> textIonLines = List.of("{id:1,value:\"value1\"}", "{id:2,value:\"value2\"}");
        Files.write(tempFile, textIonLines);

        // Read via the new InputStream-based method (should auto-detect text ION)
        try (InputStream in = new BufferedInputStream(new FileInputStream(tempFile.toFile()), FileSerde.BUFFER_SIZE)) {
            List<Map<String, Object>> results = FileSerde.readAll(in)
                .map(o -> (Map<String, Object>) o)
                .collectList()
                .block();

            assertThat(results).hasSize(2);
            assertThat(results.get(0).get("id")).isEqualTo(1);
            assertThat(results.get(0).get("value")).isEqualTo("value1");
            assertThat(results.get(1).get("id")).isEqualTo(2);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void read_consumer() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, Map.of("key1", "value1"));
            FileSerde.write(outputStream, Map.of("key2", "value2"));
            FileSerde.write(outputStream, Map.of("key3", "value3"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            FileSerde.read(inputStream, list::add);

            assertThat(list).hasSize(3);
            assertThat(((Map<String, Object>) list.get(0)).get("key1")).isEqualTo("value1");
            assertThat(((Map<String, Object>) list.get(2)).get("key3")).isEqualTo("value3");
        }
    }

    @Test
    void readAll_withTypeReference_fromInputStream() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, new SimpleEntry(1, "value1"));
            FileSerde.write(outputStream, new SimpleEntry(2, "value2"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<SimpleEntry> results = FileSerde.readAll(inputStream, new TypeReference<SimpleEntry>() {
            }).collectList().block();

            assertThat(results).hasSize(2);
            assertThat(results.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
            assertThat(results.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
        }
    }

    @Test
    void readAll_withClass_fromInputStream() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, new SimpleEntry(1, "value1"));
            FileSerde.write(outputStream, new SimpleEntry(2, "value2"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<SimpleEntry> results = FileSerde.readAll(inputStream, SimpleEntry.class)
                .collectList().block();

            assertThat(results).hasSize(2);
            assertThat(results.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
            assertThat(results.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
        }
    }

    @Test
    void writeAll_fromOutputStream() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");

        List<SimpleEntry> inputValues = List.of(new SimpleEntry(1, "value1"), new SimpleEntry(2, "value2"), new SimpleEntry(3, "value3"));
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            Long count = FileSerde.writeAll(outputStream, Flux.fromIterable(inputValues)).block();
            assertThat(count).isEqualTo(3L);
        }
        assertThat(tempFile.length()).isGreaterThan(0);

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<SimpleEntry> results = FileSerde.readAll(inputStream, SimpleEntry.class)
                .collectList().block();

            assertThat(results).hasSize(3);
            assertThat(results.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
            assertThat(results.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
            assertThat(results.get(2)).isEqualTo(new SimpleEntry(3, "value3"));
        }
    }

    @Test
    void readAll_withObjectMapper_typeReference_fromInputStream() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, new SimpleEntry(1, "value1"));
            FileSerde.write(outputStream, new SimpleEntry(2, "value2"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<SimpleEntry> results = FileSerde.readAll(JacksonMapper.ofIon(), inputStream, new TypeReference<SimpleEntry>() {
            }).collectList().block();

            assertThat(results).hasSize(2);
            assertThat(results.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
            assertThat(results.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
        }
    }

    @Test
    void readAll_withObjectMapper_class_fromInputStream() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, new SimpleEntry(1, "value1"));
            FileSerde.write(outputStream, new SimpleEntry(2, "value2"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<SimpleEntry> results = FileSerde.readAll(JacksonMapper.ofIon(), inputStream, SimpleEntry.class)
                .collectList().block();

            assertThat(results).hasSize(2);
            assertThat(results.getFirst()).isEqualTo(new SimpleEntry(1, "value1"));
            assertThat(results.get(1)).isEqualTo(new SimpleEntry(2, "value2"));
        }
    }

    private static Path createTempFile() throws IOException {
        return Files.createTempFile(FileSerdeTest.class.getSimpleName().toLowerCase() + "_", ".ion");
    }

    private record SimpleEntry(long id, String value) {
    }
}