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
     * It is advised to wrap all readers and writers with buffered variants before calling any of the methods here.
     * We advise a buffer of BUFFER_SIZE which is 32k.
     */
    public static final int BUFFER_SIZE = 32 * 1024;

    private static final ObjectMapper DEFAULT_OBJECT_MAPPER = JacksonMapper.ofIon();
    private static final ObjectMapper JSON_OBJECT_MAPPER = JacksonMapper.ofJson();
    private static final TypeReference<Object> DEFAULT_TYPE_REFERENCE = new TypeReference<>() {
    };

    private FileSerde() {
    }

    public static void write(OutputStream output, Object row) throws IOException {
        if (row != null) { // avoid writing "null"
            output.write(DEFAULT_OBJECT_MAPPER.writeValueAsBytes(row));
            output.write("\n".getBytes());
        }
    }

    /**
     * @deprecated use the {@link #readAll(Reader)} method instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static Consumer<FluxSink<Object>> reader(BufferedReader input) {
        return s ->
        {
            String row;

            try {
                while ((row = input.readLine()) != null) {
                    s.next(convert(row));
                }
                s.complete();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /**
     * @deprecated use the {@link #readAll(Reader, Class)} method instead.
     */
    @Deprecated(since = "0.19", forRemoval = true)
    public static <T> Consumer<FluxSink<T>> reader(BufferedReader input, Class<T> cls) {
        return s ->
        {
            String row;

            try {
                while ((row = input.readLine()) != null) {
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
        while ((row = input.readLine()) != null) {
            consumer.accept(convert(row));
        }
    }

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
    public static Flux<Object> readAll(Reader reader) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, DEFAULT_TYPE_REFERENCE);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(Reader reader, TypeReference<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, type);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(Reader reader, Class<T> type) throws IOException {
        return readAll(DEFAULT_OBJECT_MAPPER, reader, type);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    public static Flux<Object> readAll(ObjectMapper objectMapper, Reader in) throws IOException {
        return readAll(objectMapper, in, DEFAULT_TYPE_REFERENCE);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, Reader reader, TypeReference<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, reader, type);
        return readAll(mappingIterator);
    }

    /**
     * For performance, it is advised to wrap the reader inside a BufferedReader, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(ObjectMapper objectMapper, Reader reader, Class<T> type) throws IOException {
        MappingIterator<T> mappingIterator = createMappingIterator(objectMapper, reader, type);
        return readAll(mappingIterator);
    }

    public static <T> Flux<T> readAll(MappingIterator<T> mappingIterator) throws IOException {
        return Flux.<T> create(sink ->
        {
            mappingIterator.forEachRemaining(sink::next);
            sink.complete();
        }, FluxSink.OverflowStrategy.BUFFER)
            .doFinally(throwConsumer(ignored -> mappingIterator.close()));
    }

    /**
     * For performance, it is advised to wrap the writer inside a BufferedWriter, see {@link #BUFFER_SIZE}.
     */
    public static <T> Mono<Long> writeAll(Writer writer, Flux<T> values) throws IOException {
        return writeAll(DEFAULT_OBJECT_MAPPER, writer, values);
    }

    /**
     * For performance, it is advised to wrap the writer inside a BufferedWriter, see {@link #BUFFER_SIZE}.
     */
    public static <T> Mono<Long> writeAll(ObjectMapper objectMapper, Writer writer, Flux<T> values) throws IOException {
        SequenceWriter seqWriter = createSequenceWriter(objectMapper, writer, new TypeReference<T>() {
        });
        return writeAll(values, seqWriter);
    }

    public static <T> Mono<Long> writeAll(Flux<T> values, SequenceWriter seqWriter) throws IOException {
        return values
            .filter(Objects::nonNull)
            .doOnNext(throwConsumer(seqWriter::write))
            .doFinally(throwConsumer(ignored -> seqWriter.flush())) // we should have called close() but it generates an exception, so we flush
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

    // region InputStream-based read methods (compatible with both text and binary ION in 2.0+)

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static Flux<Object> readAll(InputStream inputStream) throws IOException {
        return readAll(new BufferedReader(new InputStreamReader(inputStream), BUFFER_SIZE));
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(InputStream inputStream, TypeReference<T> type) throws IOException {
        return readAll(new BufferedReader(new InputStreamReader(inputStream), BUFFER_SIZE), type);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Flux<T> readAll(InputStream inputStream, Class<T> type) throws IOException {
        return readAll(new BufferedReader(new InputStreamReader(inputStream), BUFFER_SIZE), type);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static void read(InputStream input, Consumer<Object> consumer) throws IOException {
        reader(new BufferedReader(new InputStreamReader(input), BUFFER_SIZE), consumer);
    }

    /**
     * For performance, it is advised to wrap the input stream inside a BufferedInputStream, see {@link #BUFFER_SIZE}.
     */
    public static boolean read(InputStream input, int maxLines, Consumer<Object> consumer) throws IOException {
        return reader(new BufferedReader(new InputStreamReader(input), BUFFER_SIZE), maxLines, consumer);
    }

    // endregion

    // region OutputStream-based write methods (compatible with binary ION in 2.0+)

    /**
     * For performance, it is advised to wrap the output stream inside a BufferedOutputStream, see {@link #BUFFER_SIZE}.
     */
    public static <T> Mono<Long> writeAll(OutputStream outputStream, Flux<T> values) throws IOException {
        return writeAll(new BufferedWriter(new OutputStreamWriter(outputStream), BUFFER_SIZE), values);
    }

    // endregion

    public static <T> SequenceWriter createSequenceWriter(ObjectMapper objectMapper, Writer writer, TypeReference<T> type) throws IOException {
        return objectMapper.writerFor(type).writeValues(writer);
    }

    public static <T> SequenceWriter createJsonSequenceWriter(Writer writer, TypeReference<T> type) throws IOException {
        return JSON_OBJECT_MAPPER.writerFor(type).withRootValueSeparator("\n").writeValues(writer);
    }
}
