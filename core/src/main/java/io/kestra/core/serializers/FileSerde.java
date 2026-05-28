package io.kestra.core.serializers;

import java.io.*;
import java.util.Objects;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public final class FileSerde {
    /**
     * Advised buffer size for better performance. <br>
     * It is advised to wrap all input streams with buffered variants before calling any of the methods here.
     * We advise a buffer of BUFFER_SIZE which is 32k.
     */
    public static final int BUFFER_SIZE = 32 * 1024;

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JacksonMapper.ofIon();
    private static final ObjectMapper BINARY_OBJECT_MAPPER = JacksonMapper.ofIonBinary();
    private static final ObjectMapper JSON_OBJECT_MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Object> DEFAULT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private FileSerde() {
    }

    public static void write(OutputStream output, Object row) throws IOException {
        if (row != null) {
            output.write(BINARY_OBJECT_MAPPER.writeValueAsBytes(row));
        }
    }

    // region InputStream-based read methods (auto-detect text and binary ION)

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static void read(InputStream input, Consumer<Object> consumer) throws IOException {
        MappingIterator<Object> iterator = createMappingIterator(DEFAULT_OBJECT_MAPPER, input, DEFAULT_TYPE_REFERENCE);
        try (iterator) {
            while (iterator.hasNext()) {
                consumer.accept(iterator.next());
            }
        }
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static boolean read(InputStream input, int maxLines, Consumer<Object> consumer) throws IOException {
        MappingIterator<Object> iterator = createMappingIterator(DEFAULT_OBJECT_MAPPER, input, DEFAULT_TYPE_REFERENCE);
        try (iterator) {
            int nbLines = 0;
            while (iterator.hasNext()) {
                if (nbLines >= maxLines) {
                    return true;
                }
                consumer.accept(iterator.next());
                nbLines++;
            }
        }
        return false;
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static Flux<Object> readAll(InputStream inputStream) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, inputStream, DEFAULT_TYPE_REFERENCE);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(InputStream inputStream, TypeReference<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, inputStream, type);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(InputStream inputStream, Class<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, inputStream, type);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, InputStream inputStream, TypeReference<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, inputStream, type);
        return readAll(mappingIterator);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, InputStream inputStream, Class<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, inputStream, type);
        return readAll(mappingIterator);
    }

    // endregion

    // region OutputStream-based write methods (binary ION)

    /**
     * For performance, it is advised to wrap the output stream inside a BufferedOutputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Mono<Long> writeAll(OutputStream outputStream, Flux<T> values) throws IOException {
        SequenceWriter seqWriter = BINARY_OBJECT_MAPPER.writerFor(new TypeReference<T>() {
        }).writeValues(outputStream);
        return writeAll(values, seqWriter);
    }

    // endregion

    // region Reader-based read methods (text ION only — deprecated, use InputStream-based methods instead)

    @Deprecated(forRemoval = true, since = "2.0.0")
    public static void reader(BufferedReader input, Consumer<Object> consumer) throws IOException {
        String row;
        while ((row = input.readLine()) != null) {
            consumer.accept(convert(row));
        }
    }

    @Deprecated(forRemoval = true, since = "2.0.0")
    public static boolean reader(BufferedReader input, int maxLines, Consumer<Object> consumer) throws IOException {
        String row;
        int nbLines = 0;
        while ((row = input.readLine()) != null) {
            if (nbLines >= maxLines) {
                return true;
            }

            consumer.accept(convert(row));
            nbLines++;
        }

        return false;
    }

    private static Object convert(String row) throws JsonProcessingException {
        return DEFAULT_OBJECT_MAPPER.readValue(row, DEFAULT_TYPE_REFERENCE);
    }

    private static <T> T convert(String row, Class<T> cls) throws JsonProcessingException {
        return DEFAULT_OBJECT_MAPPER.readValue(row, cls);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static Flux<Object> readAll(Reader reader) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, DEFAULT_TYPE_REFERENCE);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Flux<T> readAll(Reader reader, TypeReference<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, type);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Flux<T> readAll(Reader reader, Class<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, type);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static Flux<Object> readAll(ObjectMapper objectMapper, Reader in) throws IOException {
        return readAll(objectMapper, in, DEFAULT_TYPE_REFERENCE);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, Reader reader, TypeReference<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, reader, type);
        return readAll(mappingIterator);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, Reader reader, Class<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, reader, type);
        return readAll(mappingIterator);
    }

    // endregion

    // region Writer-based write methods (text ION — deprecated, use OutputStream-based methods instead)

    /**
     * For performance, it is advised to wrap the writer inside a BufferedWriter, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Mono<Long> writeAll(Writer writer, Flux<T> values) throws IOException {
        return writeAll(DEFAULT_OBJECT_MAPPER, writer, values);
    }

    /**
     * For performance, it is advised to wrap the writer inside a BufferedWriter, see {@link #BUFFER_SIZE}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public static <T> Mono<Long> writeAll(ObjectMapper objectMapper, Writer writer, Flux<T> values) throws IOException {
        SequenceWriter seqWriter = createSequenceWriter(objectMapper, writer, new TypeReference<T>() {
        });
        return values
            .filter(Objects::nonNull)
            .doOnNext(throwConsumer(seqWriter::write))
            .doFinally(throwConsumer(ignored -> seqWriter.flush()))
            .count();
    }

    // endregion

    public static <T> Flux<T> readAll(MappingIterator<T> mappingIterator) throws IOException {
        return Flux.<T> create(sink ->
        {
            mappingIterator.forEachRemaining(sink::next);
            sink.complete();
        }, FluxSink.OverflowStrategy.BUFFER)
            .doFinally(throwConsumer(ignored -> mappingIterator.close()));
    }

    public static <T> Mono<Long> writeAll(Flux<T> values, SequenceWriter seqWriter) throws IOException {
        return values
            .filter(Objects::nonNull)
            .doOnNext(throwConsumer(seqWriter::write))
            .doFinally(throwConsumer(ignored -> seqWriter.close()))
            .count();
    }

    private static <T> MappingIterator<T> createMappingIterator(ObjectMapper objectMapper, Reader reader, TypeReference<T> type) throws IOException {
        try (var parser = objectMapper.createParser(reader)) {
            return objectMapper.readerFor(type).readValues(parser);
        }
    }

    private static <T> MappingIterator<T> createMappingIterator(ObjectMapper objectMapper, Reader reader, Class<T> type) throws IOException {
        try (var parser = objectMapper.createParser(reader)) {
            return objectMapper.readerFor(type).readValues(parser);
        }
    }

    private static <T> MappingIterator<T> createMappingIterator(ObjectMapper objectMapper, InputStream inputStream, TypeReference<T> type) throws IOException {
        try (var parser = objectMapper.createParser(inputStream)) {
            return objectMapper.readerFor(type).readValues(parser);
        }
    }

    private static <T> MappingIterator<T> createMappingIterator(ObjectMapper objectMapper, InputStream inputStream, Class<T> type) throws IOException {
        try (var parser = objectMapper.createParser(inputStream)) {
            return objectMapper.readerFor(type).readValues(parser);
        }
    }

    public static <T> SequenceWriter createSequenceWriter(ObjectMapper objectMapper, Writer writer, TypeReference<T> type) throws IOException {
        return objectMapper.writerFor(type).writeValues(writer);
    }

    public static <T> SequenceWriter createJsonSequenceWriter(Writer writer, TypeReference<T> type) throws IOException {
        return JSON_OBJECT_MAPPER.writerFor(type).withRootValueSeparator("\n").writeValues(writer);
    }
}
