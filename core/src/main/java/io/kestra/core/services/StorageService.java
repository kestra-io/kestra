package io.kestra.core.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class StorageService {

    private static final ObjectMapper ION_MAPPER = JacksonMapper.ofIon();

    public static List<URI> split(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from) throws IOException, IllegalVariableEvaluationException {
        String fromPath = from.getPath();
        String extension = ".tmp";
        if (fromPath.indexOf('.') >= 0) {
            extension = fromPath.substring(fromPath.lastIndexOf('.'));
        }

        boolean isIon = extension.equals(".ion");

        if (isIon) {
            return splitIon(runContext, storageSplitInterface, from, extension);
        } else {
            return splitText(runContext, storageSplitInterface, from, extension);
        }
    }

    private static List<URI> splitIon(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from, String extension) throws IOException, IllegalVariableEvaluationException {
        try (InputStream inputStream = new BufferedInputStream(runContext.storage().getFile(from), FileSerde.BUFFER_SIZE)) {
            List<Path> splited;

            if (storageSplitInterface.getRegexPattern() != null) {
                String renderedPattern = runContext.render(storageSplitInterface.getRegexPattern()).as(String.class).orElseThrow();
                splited = splitIonByRegex(runContext, extension, inputStream, renderedPattern);
            } else if (storageSplitInterface.getBytes() != null) {
                ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
                Number convert = readableBytesTypeConverter.convert(runContext.render(storageSplitInterface.getBytes()).as(String.class).orElseThrow(), Number.class)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + storageSplitInterface.getBytes() + "'"));

                splited = splitIonByPredicate(runContext, extension, inputStream, (bytes, size) -> bytes >= convert.longValue());
            } else if (storageSplitInterface.getPartitions() != null) {
                splited = partitionIon(
                    runContext, extension, inputStream,
                    runContext.render(storageSplitInterface.getPartitions()).as(Integer.class).orElseThrow()
                );
            } else if (storageSplitInterface.getRows() != null) {
                Integer renderedRows = runContext.render(storageSplitInterface.getRows()).as(Integer.class).orElseThrow();
                splited = splitIonByPredicate(runContext, extension, inputStream, (bytes, size) -> size >= renderedRows);
            } else {
                throw new IllegalArgumentException("Invalid configuration with no size, count, rows, nor regexPattern");
            }

            return splited.stream()
                .map(throwFunction(path -> runContext.storage().putFile(path.toFile())))
                .toList();
        }
    }

    private static List<Path> splitIonByPredicate(RunContext runContext, String extension, InputStream inputStream, BiFunction<Integer, Integer, Boolean> predicate)
        throws IOException {
        List<Path> files = new ArrayList<>();
        OutputStream currentOutput = null;
        int totalBytes = 0;
        int totalRows = 0;

        try {
            var iterator = ION_MAPPER.readerFor(Object.class).readValues(ION_MAPPER.createParser(inputStream));
            while (iterator.hasNext()) {
                Object record = iterator.next();
                if (currentOutput == null || predicate.apply(totalBytes, totalRows)) {
                    if (currentOutput != null) {
                        currentOutput.close();
                    }

                    totalBytes = 0;
                    totalRows = 0;

                    Path path = runContext.workingDir().createTempFile(extension);
                    files.add(path);
                    currentOutput = new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE);
                }

                byte[] bytes = JacksonMapper.ofIonBinary().writeValueAsBytes(record);
                currentOutput.write(bytes);
                totalBytes = totalBytes + bytes.length;
                totalRows = totalRows + 1;
            }
        } finally {
            if (currentOutput != null) {
                currentOutput.close();
            }
        }

        return files;
    }

    private static List<Path> partitionIon(RunContext runContext, String extension, InputStream inputStream, int partition) throws IOException {
        List<Path> files = new ArrayList<>();
        List<OutputStream> writers = new ArrayList<>();

        try {
            for (int i = 0; i < partition; i++) {
                Path path = runContext.workingDir().createTempFile(extension);
                files.add(path);
                writers.add(new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE));
            }

            int index = 0;
            var iterator = ION_MAPPER.readerFor(Object.class).readValues(ION_MAPPER.createParser(inputStream));
            while (iterator.hasNext()) {
                FileSerde.write(writers.get(index), iterator.next());
                index = index >= writers.size() - 1 ? 0 : index + 1;
            }

            return files.stream().filter(p -> p.toFile().length() > 0).toList();
        } finally {
            for (OutputStream w : writers) {
                try {
                    w.close();
                } catch (IOException e) {
                    runContext.logger().error("Failed to close partition writer", e);
                }
            }
        }
    }

    private static List<Path> splitIonByRegex(RunContext runContext, String extension, InputStream inputStream, String regexPattern) throws IOException {
        List<Path> files = new ArrayList<>();
        Map<String, OutputStream> writers = new HashMap<>();
        Pattern pattern = Pattern.compile(regexPattern);

        try {
            var iterator = ION_MAPPER.readerFor(Object.class).readValues(ION_MAPPER.createParser(inputStream));
            while (iterator.hasNext()) {
                Object record = iterator.next();
                String textIon = ION_MAPPER.writeValueAsString(record);
                Matcher matcher = RegexUtils.matcher(pattern, textIon);

                if (matcher.find() && matcher.groupCount() > 0) {
                    String routingKey = matcher.group(1);

                    OutputStream writer = writers.get(routingKey);
                    if (writer == null) {
                        Path path = runContext.workingDir().createTempFile(extension);
                        files.add(path);
                        writer = new BufferedOutputStream(new FileOutputStream(path.toFile()), FileSerde.BUFFER_SIZE);
                        writers.put(routingKey, writer);
                    }

                    FileSerde.write(writer, record);
                }
            }
        } finally {
            writers.values().forEach(throwConsumer(OutputStream::close));
        }

        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

    // Text-based splitting for non-ION files (original behavior)

    private static List<URI> splitText(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from, String extension) throws IOException, IllegalVariableEvaluationException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(from)))) {
            List<Path> splited;

            if (storageSplitInterface.getRegexPattern() != null) {
                String renderedPattern = runContext.render(storageSplitInterface.getRegexPattern()).as(String.class).orElseThrow();
                String separator = runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow();
                splited = splitTextByRegex(runContext, extension, separator, bufferedReader, renderedPattern);
            } else if (storageSplitInterface.getBytes() != null) {
                ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
                Number convert = readableBytesTypeConverter.convert(runContext.render(storageSplitInterface.getBytes()).as(String.class).orElseThrow(), Number.class)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + storageSplitInterface.getBytes() + "'"));

                splited = splitTextByPredicate(
                    runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, (bytes, size) -> bytes >= convert.longValue()
                );
            } else if (storageSplitInterface.getPartitions() != null) {
                splited = partitionText(
                    runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, runContext.render(storageSplitInterface.getPartitions()).as(Integer.class).orElseThrow()
                );
            } else if (storageSplitInterface.getRows() != null) {
                Integer renderedRows = runContext.render(storageSplitInterface.getRows()).as(Integer.class).orElseThrow();
                splited = splitTextByPredicate(
                    runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, (bytes, size) -> size >= renderedRows
                );
            } else {
                throw new IllegalArgumentException("Invalid configuration with no size, count, rows, nor regexPattern");
            }

            return splited
                .stream()
                .map(throwFunction(path -> runContext.storage().putFile(path.toFile())))
                .toList();
        }
    }

    private static List<Path> splitTextByPredicate(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, BiFunction<Integer, Integer, Boolean> predicate)
        throws IOException {
        List<Path> files = new ArrayList<>();
        RandomAccessFile write = null;
        int totalBytes = 0;
        int totalRows = 0;
        String row;

        while ((row = bufferedReader.readLine()) != null) {
            if (write == null || predicate.apply(totalBytes, totalRows)) {
                if (write != null) {
                    write.close();
                }

                totalBytes = 0;
                totalRows = 0;

                Path path = runContext.workingDir().createTempFile(extension);
                files.add(path);
                write = new RandomAccessFile(path.toFile(), "rw");
            }

            byte[] bytes = (row + separator).getBytes(StandardCharsets.UTF_8);

            write.getChannel().write(ByteBuffer.wrap(bytes));

            totalBytes = totalBytes + bytes.length;
            totalRows = totalRows + 1;
        }

        if (write != null) {
            write.close();
        }

        return files;
    }

    private static List<Path> partitionText(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, int partition) throws IOException {
        List<Path> files = new ArrayList<>();
        List<RandomAccessFile> writers = new ArrayList<>();

        try {
            for (int i = 0; i < partition; i++) {
                Path path = runContext.workingDir().createTempFile(extension);
                files.add(path);

                writers.add(new RandomAccessFile(path.toFile(), "rw"));
            }

            String row;
            int index = 0;
            while ((row = bufferedReader.readLine()) != null) {
                writers.get(index).getChannel().write(ByteBuffer.wrap((row + separator).getBytes(StandardCharsets.UTF_8)));

                index = index >= writers.size() - 1 ? 0 : index + 1;
            }

            return files.stream().filter(p -> p.toFile().length() > 0).toList();
        } finally {
            for (RandomAccessFile w : writers) {
                try {
                    w.close();
                } catch (IOException e) {
                    runContext.logger().error("Failed to close partition writer", e);
                }
            }
        }
    }

    private static List<Path> splitTextByRegex(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, String regexPattern) throws IOException {
        List<Path> files = new ArrayList<>();
        Map<String, RandomAccessFile> writers = new HashMap<>();
        Pattern pattern = Pattern.compile(regexPattern);

        String row;
        while ((row = bufferedReader.readLine()) != null) {
            Matcher matcher = RegexUtils.matcher(pattern, row);

            if (matcher.find() && matcher.groupCount() > 0) {
                String routingKey = matcher.group(1);

                RandomAccessFile writer = writers.get(routingKey);
                if (writer == null) {
                    Path path = runContext.workingDir().createTempFile(extension);
                    files.add(path);
                    writer = new RandomAccessFile(path.toFile(), "rw");
                    writers.put(routingKey, writer);
                }

                byte[] bytes = (row + separator).getBytes(StandardCharsets.UTF_8);
                writer.getChannel().write(ByteBuffer.wrap(bytes));
            }
        }

        writers.values().forEach(throwConsumer(RandomAccessFile::close));

        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

}
