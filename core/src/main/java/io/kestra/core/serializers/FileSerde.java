package io.kestra.core.serializers;

import io.github.pixee.security.BoundedLineReader;
import static io.kestra.core.utils.Rethrow.throwBiFunction;
import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Consumer;

public final class FileSerde {

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JacksonMapper.ofIon();
    private static final TypeReference<Object> DEFAULT_TYPE_REFERENCE = new TypeReference<>(){};

    /** The size of the buffer used for reading and writing data from streams. */
    private static final int BUFFER_SIZE = 32 * 1024;

    private FileSerde() {}

    public static void write(OutputStream output, Object row) throws IOException {
        if (row != null) { // avoid writing "null"
            output.write(DEFAULT_OBJECT_MAPPER.writeValueAsBytes(row));
            output.write("\n".getBytes());
        }
    }

    public static Consumer<FluxSink<Object>> reader(BufferedReader input) {
        return s -> {
            String row;

            try {
                while ((row = BoundedLineReader.readLine(input, 5_000_000)) != null) {
                    s.next(convert(row));
                }
                s.complete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static <T> Consumer<FluxSink<T>> reader(BufferedReader input, Class<T> cls) {
        return s -> {
            String row;

            try {
                while ((row = BoundedLineReader.readLine(input, 5_000_000)) != null) {
                    s.next(convert(row, cls));
                }
                s.complete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    public static void reader(BufferedReader input, Consumer<Object> consumer) throws IOException {
        String row;
        while ((row = BoundedLineReader.readLine(input, 5_000_000)) != null) {
            consumer.accept(convert(row));
        }
    }

    public static boolean reader(BufferedReader input, int maxLines, Consumer<Object> consumer) throws IOException {
        String row;
        int nbLines = 0;
        while ((row = BoundedLineReader.readLine(input, 5_000_000)) != null) {
            if (nbLines >= maxLines) {
                return true;
            }

            consumer.accept(convert(row));
            nbLines ++;
        }

        return false;
    }

    private static Object convert(String row) throws JsonProcessingException {
        return DEFAULT_OBJECT_MAPPER.readValue(row, DEFAULT_TYPE_REFERENCE);
    }

    private static <T> T convert(String row, Class<T> cls) throws JsonProcessingException {
        return DEFAULT_OBJECT_MAPPER.readValue(row, cls);
    }

    public static Flux<Object> readAll(InputStream in) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, in, DEFAULT_TYPE_REFERENCE);
    }

    public static <T> Flux<T> readAll(InputStream in, TypeReference<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, in, type);
    }

    public static Flux<Object> readAll(ObjectMapper objectMapper, InputStream in) throws IOException {
        return readAll(objectMapper, in, DEFAULT_TYPE_REFERENCE);
    }

    public static <T> Flux<T> readAll(ObjectMapper objectMapper, InputStream in, TypeReference<T> type) throws IOException {
        return Flux.generate(
            () -> createMappingIterator(objectMapper, in, type),
            throwBiFunction((iterator, sink) -> {
                final T value = iterator.hasNextValue() ? iterator.nextValue() : null;
                Optional.ofNullable(value).ifPresentOrElse(sink::next, sink::complete);
                return iterator;
            }),
            throwConsumer(MappingIterator::close)
        );
    }

    public static <T> Mono<Long> writeAll(OutputStream out, Flux<T> values) throws IOException {
        return writeAll(DEFAULT_OBJECT_MAPPER, out, values);
    }

    public static <T> Mono<Long> writeAll(ObjectMapper objectMapper, OutputStream out, Flux<T> values) throws IOException {
        return Mono.using(
            () -> createSequenceWriter(objectMapper, out),
            throwFunction((writer) -> values.doOnNext(throwConsumer(writer::write)).count()),
            throwConsumer(SequenceWriter::close)
        );
    }

    private static <T> MappingIterator<T> createMappingIterator(ObjectMapper objectMapper, InputStream in, TypeReference<T> type) throws IOException {
        return objectMapper.readerFor(type).readValues(new BufferedInputStream(in, BUFFER_SIZE));
    }

    private static SequenceWriter createSequenceWriter(ObjectMapper objectMapper, OutputStream out) throws IOException {
        return objectMapper.writer().writeValues(new BufferedOutputStream(out, BUFFER_SIZE));
    }
}
