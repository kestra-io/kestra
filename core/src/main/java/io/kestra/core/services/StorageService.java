package io.kestra.core.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.RegexUtils;
import io.kestra.core.storages.StorageSplitInterface;

import com.amazon.ion.util.IonStreamUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;

import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class StorageService {

    private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();
    private static final ObjectMapper ION_BINARY_MAPPER = JacksonMapper.ofIonBinary();
    private static final TypeReference<Object> OBJECT_TYPE = new TypeReference<>() {};

    public static List<URI> split(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from) throws IOException, IllegalVariableEvaluationException {
        String extension = extensionOf(from);

        try (BufferedInputStream inputStream = new BufferedInputStream(runContext.storage().getFile(from), FileSerde.BUFFER_SIZE)) {
            inputStream.mark(4);
            byte[] header = inputStream.readNBytes(4);
            inputStream.reset();

            List<Path> splited;
            try (SplitStrategy strategy = IonStreamUtils.isIonBinary(header)
                ? new IonSplitStrategy(inputStream)
                : new TextSplitStrategy(inputStream, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow())) {

                if (storageSplitInterface.getRegexPattern() != null) {
                    String renderedPattern = runContext.render(storageSplitInterface.getRegexPattern()).as(String.class).orElseThrow();
                    splited = splitByRegex(runContext, extension, strategy, renderedPattern);
                } else if (storageSplitInterface.getBytes() != null) {
                    long maxBytes = parseBytes(runContext, storageSplitInterface);
                    splited = splitByPredicate(runContext, extension, strategy, (bytes, size) -> bytes >= maxBytes);
                } else if (storageSplitInterface.getPartitions() != null) {
                    int partitions = runContext.render(storageSplitInterface.getPartitions()).as(Integer.class).orElseThrow();
                    splited = partition(runContext, extension, strategy, partitions);
                } else if (storageSplitInterface.getRows() != null) {
                    int rows = runContext.render(storageSplitInterface.getRows()).as(Integer.class).orElseThrow();
                    splited = splitByPredicate(runContext, extension, strategy, (bytes, size) -> size >= rows);
                } else {
                    throw new IllegalArgumentException("Invalid configuration with no size, count, rows, nor regexPattern");
                }
            }

            return splited.stream()
                .map(throwFunction(path -> runContext.storage().putFile(path.toFile())))
                .toList();
        }
    }

    private static String extensionOf(URI from) {
        String fromPath = from.getPath();
        if (fromPath.indexOf('.') >= 0) {
            return fromPath.substring(fromPath.lastIndexOf('.'));
        }
        return ".tmp";
    }

    private static long parseBytes(RunContext runContext, StorageSplitInterface storageSplitInterface) throws IllegalVariableEvaluationException {
        ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
        Number convert = readableBytesTypeConverter.convert(runContext.render(storageSplitInterface.getBytes()).as(String.class).orElseThrow(), Number.class)
            .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + storageSplitInterface.getBytes() + "'"));
        return convert.longValue();
    }

    // region split operations (format-agnostic, parameterized by a SplitStrategy)

    private static List<Path> splitByPredicate(RunContext runContext, String extension, SplitStrategy strategy, BiFunction<Integer, Integer, Boolean> predicate)
        throws IOException {
        List<Path> files = new ArrayList<>();
        RecordWriter writer = null;
        int totalBytes = 0;
        int totalRows = 0;

        try {
            Iterator<Object> iterator = strategy.records();
            while (iterator.hasNext()) {
                Object record = iterator.next();
                if (writer == null || predicate.apply(totalBytes, totalRows)) {
                    if (writer != null) {
                        writer.close();
                    }

                    totalBytes = 0;
                    totalRows = 0;

                    Path path = runContext.workingDir().createTempFile(extension);
                    files.add(path);
                    writer = strategy.newWriter(path);
                }

                totalBytes = totalBytes + writer.write(record);
                totalRows = totalRows + 1;
            }
        } finally {
            if (writer != null) {
                writer.close();
            }
        }

        return files;
    }

    private static List<Path> partition(RunContext runContext, String extension, SplitStrategy strategy, int partition) throws IOException {
        List<Path> files = new ArrayList<>();
        List<RecordWriter> writers = new ArrayList<>();

        try {
            for (int i = 0; i < partition; i++) {
                Path path = runContext.workingDir().createTempFile(extension);
                files.add(path);
                writers.add(strategy.newWriter(path));
            }

            int index = 0;
            Iterator<Object> iterator = strategy.records();
            while (iterator.hasNext()) {
                writers.get(index).write(iterator.next());
                index = index >= writers.size() - 1 ? 0 : index + 1;
            }
        } finally {
            closeQuietly(runContext, writers);
        }

        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

    private static List<Path> splitByRegex(RunContext runContext, String extension, SplitStrategy strategy, String regexPattern) throws IOException {
        List<Path> files = new ArrayList<>();
        Map<String, RecordWriter> writers = new HashMap<>();
        Pattern pattern = Pattern.compile(regexPattern);

        try {
            Iterator<Object> iterator = strategy.records();
            while (iterator.hasNext()) {
                Object record = iterator.next();
                Matcher matcher = RegexUtils.matcher(pattern, strategy.routingText(record));

                if (matcher.find() && matcher.groupCount() > 0) {
                    String routingKey = matcher.group(1);

                    RecordWriter writer = writers.get(routingKey);
                    if (writer == null) {
                        Path path = runContext.workingDir().createTempFile(extension);
                        files.add(path);
                        writer = strategy.newWriter(path);
                        writers.put(routingKey, writer);
                    }

                    writer.write(record);
                }
            }
        } finally {
            closeQuietly(runContext, writers.values());
        }

        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

    private static void closeQuietly(RunContext runContext, Iterable<RecordWriter> writers) {
        for (RecordWriter writer : writers) {
            try {
                writer.close();
            } catch (IOException e) {
                runContext.logger().error("Failed to close split writer", e);
            }
        }
    }

    // endregion

    // region split strategies (encapsulate the ION-vs-text read/write differences)

    /**
     * Reads records and creates per-output-file writers for a given file format.
     * The {@code split*} operations above are written once against this contract.
     */
    private interface SplitStrategy extends Closeable {
        /** Lazily streamed records. ION yields {@code Object} values, text yields the {@code String} rows. */
        Iterator<Object> records() throws IOException;

        /** Opens a fresh writer for a single output file. */
        RecordWriter newWriter(Path path) throws IOException;

        /** The text used for regex routing-key extraction: text-ion for ION, the raw row for text. */
        String routingText(Object record) throws IOException;
    }

    private interface RecordWriter extends Closeable {
        /** Writes one record and returns the number of bytes it represents (used by the {@code bytes} predicate). */
        int write(Object record) throws IOException;
    }

    /** Streams binary ION records and writes each output file as a single binary ION stream. */
    private static final class IonSplitStrategy implements SplitStrategy {
        private final MappingIterator<Object> iterator;

        private IonSplitStrategy(InputStream inputStream) throws IOException {
            this.iterator = ION_MAPPER.readerFor(OBJECT_TYPE).readValues(ION_MAPPER.createParser(inputStream));
        }

        @Override
        public Iterator<Object> records() {
            return iterator;
        }

        @Override
        public String routingText(Object record) throws IOException {
            return ION_MAPPER.writeValueAsString(record);
        }

        @Override
        public RecordWriter newWriter(Path path) throws IOException {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE);
            SequenceWriter sequenceWriter = FileSerde.createBinarySequenceWriter(outputStream, OBJECT_TYPE);

            return new RecordWriter() {
                @Override
                public int write(Object record) throws IOException {
                    sequenceWriter.write(record);
                    // The byte metric stays the standalone record size (the pre-refactor semantics) so the
                    // `bytes` threshold remains deterministic and independent of the writer's buffering.
                    return ION_BINARY_MAPPER.writeValueAsBytes(record).length;
                }

                @Override
                public void close() throws IOException {
                    sequenceWriter.close();
                }
            };
        }

        @Override
        public void close() throws IOException {
            iterator.close();
        }
    }

    /** Streams text rows and writes each output file as {@code row + separator} lines. */
    private static final class TextSplitStrategy implements SplitStrategy {
        private final BufferedReader reader;
        private final String separator;

        private TextSplitStrategy(InputStream inputStream, String separator) {
            this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8), FileSerde.BUFFER_SIZE);
            this.separator = separator;
        }

        @Override
        public Iterator<Object> records() {
            return new Iterator<>() {
                private String next = advance();

                private String advance() {
                    try {
                        return reader.readLine();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                @Override
                public boolean hasNext() {
                    return next != null;
                }

                @Override
                public Object next() {
                    String current = next;
                    next = advance();
                    return current;
                }
            };
        }

        @Override
        public String routingText(Object record) {
            return (String) record;
        }

        @Override
        public RecordWriter newWriter(Path path) throws IOException {
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE);

            return new RecordWriter() {
                @Override
                public int write(Object record) throws IOException {
                    byte[] bytes = (record + separator).getBytes(StandardCharsets.UTF_8);
                    outputStream.write(bytes);
                    return bytes.length;
                }

                @Override
                public void close() throws IOException {
                    outputStream.close();
                }
            };
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }

    // endregion

}
