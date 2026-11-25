package io.kestra.core.serializers;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.reactivestreams.Subscription;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.MappingIterator;

import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class FileSerdeTest {
    private static final Closeable NO_STREAM = () ->
    {
    };

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
    void shouldReadValuesAfterSkippedOnesWhenSkipIsGiven() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, Map.of("a", List.of(Map.of("x", 1), Map.of("y", 2))));
            FileSerde.write(outputStream, "scalar");
            FileSerde.write(outputStream, Map.of("key3", "value3"));
            FileSerde.write(outputStream, Map.of("key4", "value4"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            boolean hasMore = FileSerde.read(inputStream, 2, 1, list::add);

            assertThat(list).containsExactly(Map.of("key3", "value3"));
            assertThat(hasMore).isTrue();
        }
    }

    @Test
    void shouldReadNothingWhenSkipExceedsTheNumberOfValues() throws IOException {
        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, Map.of("key1", "value1"));
            FileSerde.write(outputStream, Map.of("key2", "value2"));
        }

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(tempFile), FileSerde.BUFFER_SIZE)) {
            List<Object> list = new ArrayList<>();
            boolean hasMore = FileSerde.read(inputStream, 5, 2, list::add);

            assertThat(list).isEmpty();
            assertThat(hasMore).isFalse();
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
    void shouldKeepOnlyTheGivenFields() throws IOException {
        byte[] file = binary(Map.of("a", 1, "b", "unused", "c", 3), Map.of("a", 2, "b", "unused", "c", 4));

        List<Object> list = new ArrayList<>();
        boolean hasMore = FileSerde.read(new ByteArrayInputStream(file), 10, Set.of("a", "c"), list::add);

        assertThat(list).containsExactly(Map.of("a", 1, "c", 3), Map.of("a", 2, "c", 4));
        assertThat(hasMore).isFalse();
    }

    @Test
    void shouldKeepNestedValuesOfTheGivenFieldsAndSkipTheNestedOnesOfTheOthers() throws IOException {
        byte[] file = binary(Map.of("kept", Map.of("x", List.of(1, 2)), "skipped", Map.of("y", List.of(Map.of("z", 1)))));

        List<Object> list = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file), 10, Set.of("kept"), list::add);

        assertThat(list).containsExactly(Map.of("kept", Map.of("x", List.of(1, 2))));
    }

    @Test
    void shouldKeepOnlyTheGivenFieldsOfTextIon() throws IOException {
        String file = """
            {a:1,b:"unused",c:3}
            {a:2,b:"unused",c:4}
            """;

        List<Object> list = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file.getBytes(java.nio.charset.StandardCharsets.UTF_8)), 10, Set.of("a"), list::add);

        assertThat(list).containsExactly(Map.of("a", 1), Map.of("a", 2));
    }

    @Test
    void shouldReadTheGivenFieldsAsTheFullReadDoes() throws IOException {
        byte[] file = binary(Map.of("amount", 10.5, "count", 3, "at", Instant.parse("2026-11-08T10:00:00Z"), "name", "alice", "unused", "x"));

        List<Object> full = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file), 10, full::add);
        List<Object> projected = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file), 10, Set.of("amount", "count", "at", "name"), projected::add);

        Map<String, Object> expected = new HashMap<>((Map<String, Object>) full.getFirst());
        expected.remove("unused");
        assertThat(projected).containsExactly(expected);
    }

    @Test
    void shouldPassNonObjectValuesUnchangedWhenFieldsAreGiven() throws IOException {
        byte[] file = binary("scalar", List.of(1, 2), Map.of("a", 1, "b", 2));

        List<Object> list = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file), 10, Set.of("a"), list::add);

        assertThat(list).containsExactly("scalar", List.of(1, 2), Map.of("a", 1));
    }

    @Test
    void shouldStopAtMaxLinesWhenFieldsAreGiven() throws IOException {
        byte[] file = binary(Map.of("a", 1), Map.of("a", 2), Map.of("a", 3));

        List<Object> list = new ArrayList<>();
        assertThat(FileSerde.read(new ByteArrayInputStream(file), 2, Set.of("a"), list::add)).isTrue();
        assertThat(list).hasSize(2);

        list.clear();
        assertThat(FileSerde.read(new ByteArrayInputStream(file), 3, Set.of("a"), list::add)).isFalse();
        assertThat(list).hasSize(3);
    }

    @Test
    void shouldReadEmptyObjectsWhenNoFieldIsGiven() throws IOException {
        byte[] file = binary(Map.of("a", 1), Map.of("a", 2));

        List<Object> list = new ArrayList<>();
        FileSerde.read(new ByteArrayInputStream(file), 10, Set.of(), list::add);

        assertThat(list).containsExactly(Map.of(), Map.of());
    }

    private static byte[] binary(Object... rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Object row : rows) {
            FileSerde.write(out, row);
        }
        return out.toByteArray();
    }

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

    @Test
    void shouldDeserializeOnlyRequestedRecordsWhenDemandIsBounded() throws IOException {
        var deserialized = new AtomicInteger();
        var iterator = countingIterator(records(10_000), deserialized);
        var subscriber = new RecordingSubscriber();

        FileSerde.readOnDemand(iterator, NO_STREAM).subscribe(subscriber);

        subscriber.request(1);
        assertThat(deserialized).hasValue(1);
        assertThat(subscriber.received).hasSize(1);

        subscriber.request(9);
        assertThat(deserialized).hasValue(10);
        assertThat(subscriber.received).extracting(CountedEntry::id).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);

        subscriber.request(Long.MAX_VALUE);
        assertThat(subscriber.received).hasSize(10_000);
        assertThat(subscriber.completed).isTrue();
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldCompleteAndCloseSourceWhenDemandEqualsRecordCount() throws IOException {
        var iterator = countingIterator(records(10), new AtomicInteger());
        var subscriber = new RecordingSubscriber();

        FileSerde.readOnDemand(iterator, NO_STREAM).subscribe(subscriber);
        subscriber.request(10);

        assertThat(subscriber.received).hasSize(10);
        assertThat(subscriber.completed).isTrue();
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldCompleteAndCloseSourceWhenSourceIsEmpty() throws IOException {
        var iterator = countingIterator("", new AtomicInteger());
        var subscriber = new RecordingSubscriber();

        FileSerde.readOnDemand(iterator, NO_STREAM).subscribe(subscriber);
        subscriber.request(1);

        assertThat(subscriber.received).isEmpty();
        assertThat(subscriber.completed).isTrue();
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldStopReadingAndCloseSourceWhenSubscriberCancels() throws IOException {
        var deserialized = new AtomicInteger();
        var iterator = countingIterator(records(10_000), deserialized);

        var taken = FileSerde.readOnDemand(iterator, NO_STREAM)
            .take(10)
            .collectList()
            .block();

        assertThat(taken).hasSize(10);
        assertThat(deserialized).hasValue(10);
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldEmitErrorOnlyWhenMalformedRecordIsRequested() throws IOException {
        var iterator = countingIterator(records(1_000) + "{id:1000,name:\"unterminated}\n{id:1001}\n", new AtomicInteger());
        var subscriber = new RecordingSubscriber();

        FileSerde.readOnDemand(iterator, NO_STREAM).subscribe(subscriber);

        subscriber.request(1_000);
        assertThat(subscriber.received).hasSize(1_000);
        assertThat(subscriber.error).isNull();

        subscriber.request(1);
        assertThat(subscriber.error).isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(JsonProcessingException.class);
        assertThat(subscriber.received).hasSize(1_000);
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldEmitEveryRecordBeforeTheErrorWhenConsumerAsksForFusion() throws IOException {
        var iterator = countingIterator(records(5) + "}{id:5}\n", new AtomicInteger());
        var received = new ArrayList<Long>();

        assertThatThrownBy(
            () -> FileSerde.readOnDemand(iterator, NO_STREAM)
                .flatMapIterable(List::of)
                .doOnNext(entry -> received.add(entry.id()))
                .blockLast()
        ).isInstanceOf(RuntimeException.class);

        assertThat(received).containsExactly(0L, 1L, 2L, 3L, 4L);
    }

    @Test
    void shouldCloseStreamOnceWhenReadingOnDemandEnds() throws IOException {
        var closed = new AtomicInteger();
        var stream = new FilterInputStream(new ByteArrayInputStream(records(3).getBytes())) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        };

        var read = FileSerde.readOnDemand(stream, Map.class).collectList().block();

        assertThat(read).hasSize(3);
        assertThat(closed).hasValue(1);
    }

    @Test
    void shouldGiveRecordsToFirstSubscriptionOnlyWhenSubscribedTwice() throws IOException {
        var iterator = countingIterator(records(10), new AtomicInteger());
        var flux = FileSerde.readOnDemand(iterator, NO_STREAM);
        var first = new RecordingSubscriber();
        var second = new RecordingSubscriber();

        flux.subscribe(first);
        first.request(2);
        flux.subscribe(second);
        second.request(Long.MAX_VALUE);
        first.request(Long.MAX_VALUE);

        assertThat(first.received).extracting(CountedEntry::id).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L);
        assertThat(first.completed).isTrue();
        assertThat(second.received).isEmpty();
        assertThat(second.completed).isTrue();
        assertThat(second.error).isNull();
        verify(iterator, times(1)).close();
    }

    @Test
    void shouldCloseStreamWhenIteratorCloseFailsOnDemand() throws IOException {
        var closed = new AtomicInteger();
        var stream = new FilterInputStream(new ByteArrayInputStream(records(3).getBytes())) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        };
        MappingIterator<Map<String, Object>> iterator = spy(JacksonMapper.ofIon().readerFor(Map.class).readValues(stream));
        var closeError = new IOException("The iterator can't be closed.");
        doThrow(closeError).when(iterator).close();
        var dropped = new CopyOnWriteArrayList<Throwable>();

        var read = FileSerde.readOnDemand(iterator, stream)
            .collectList()
            // Context-local hook (its key constant is package-private), so no global hook leaks into parallel tests.
            .contextWrite(Context.of("reactor.onErrorDropped.local", (Consumer<Throwable>) dropped::add))
            .block();

        assertThat(read).hasSize(3);
        assertThat(closed).hasValue(1);
        assertThat(dropped).singleElement().satisfies(error -> assertThat(error).hasCause(closeError));
    }

    private static String records(int count) {
        var builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append("{id:").append(i).append("}\n");
        }
        return builder.toString();
    }

    private static MappingIterator<CountedEntry> countingIterator(String source, AtomicInteger deserialized) throws IOException {
        MappingIterator<CountedEntry> iterator = JacksonMapper.ofIon()
            .readerFor(CountedEntry.class)
            .with(new InjectableValues.Std().addValue(AtomicInteger.class, deserialized))
            .readValues(source.getBytes());
        return spy(iterator);
    }

    private record CountedEntry(long id) {
        @JsonCreator
        CountedEntry(@JsonProperty("id") long id, @JacksonInject AtomicInteger deserialized) {
            this(id);
            deserialized.incrementAndGet();
        }
    }

    private static class RecordingSubscriber extends BaseSubscriber<CountedEntry> {
        private final List<CountedEntry> received = new ArrayList<>();
        private boolean completed;
        private Throwable error;

        @Override
        protected void hookOnSubscribe(Subscription subscription) {
        }

        @Override
        protected void hookOnNext(CountedEntry value) {
            received.add(value);
        }

        @Override
        protected void hookOnComplete() {
            completed = true;
        }

        @Override
        protected void hookOnError(Throwable throwable) {
            error = throwable;
        }
    }

    private static Path createTempFile() throws IOException {
        return Files.createTempFile(FileSerdeTest.class.getSimpleName().toLowerCase() + "_", ".ion");
    }

    private record SimpleEntry(long id, String value) {
    }
}