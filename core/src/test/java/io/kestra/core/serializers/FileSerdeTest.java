package io.kestra.core.serializers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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

    @SuppressWarnings({"unchecked", "rawtypes"})
    @ParameterizedTest
    @MethodSource("source")
    void ion(Object value, Object resultValue) throws IOException {
        Map<String, Object> object = new HashMap<>();
        object.put("key", value);

        File tempFile = File.createTempFile(this.getClass().getSimpleName().toLowerCase() + "_", ".ion");
        try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
            FileSerde.write(outputStream, object);
        }

        BufferedReader inputStream = new BufferedReader(new FileReader(tempFile));

        Map<String, Object> result = Flux
            .create(FileSerde.reader(inputStream), FluxSink.OverflowStrategy.BUFFER)
            .map(o -> (Map<String, Object>) o)
            .blockFirst();

        if (value instanceof Map) {
            assertThat(((Map) object.get("key")).entrySet(), everyItem(is(in(((Map) result.get("key")).entrySet()))));
            assertThat(((Map) result.get("key")).entrySet(), everyItem(is(in(((Map) object.get("key")).entrySet()))));
        } else if (value instanceof Collections) {
            assertThat((List) object.get("key"), containsInAnyOrder((List) result.get("key")));
        } else {
            assertThat(result.get("key"), is(resultValue != null ? resultValue : object.get("key")));
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

        BufferedReader inputStream = new BufferedReader(new FileReader(tempFile));

        List<Object> list = new ArrayList<>();
        FileSerde.reader(inputStream, 2, row -> list.add(row));

        assertThat(list.size(), is(2));
    }
}