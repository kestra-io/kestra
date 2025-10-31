package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageSplitInterface;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
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

import static io.kestra.core.utils.Rethrow.throwConsumer;
import static io.kestra.core.utils.Rethrow.throwFunction;

public abstract class StorageService {

    public static List<URI> split(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from) throws IOException, IllegalVariableEvaluationException {
        String fromPath = from.getPath();
        String extension = ".tmp";
        if (fromPath.indexOf('.') >= 0) {
            extension = fromPath.substring(fromPath.lastIndexOf('.'));
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(from)))) {
            List<Path> splited;

            if (storageSplitInterface.getRegexPattern() != null) {
                String renderedPattern = runContext.render(storageSplitInterface.getRegexPattern()).as(String.class).orElseThrow();
                String separator = runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow();
                splited = StorageService.splitByRegex(runContext, extension, separator, bufferedReader, renderedPattern);
            } else if (storageSplitInterface.getBytes() != null) {
                ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
                Number convert = readableBytesTypeConverter.convert(runContext.render(storageSplitInterface.getBytes()).as(String.class).orElseThrow(), Number.class)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + storageSplitInterface.getBytes() + "'"));

                splited = StorageService.split(runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, (bytes, size) -> bytes >= convert.longValue());
            } else if (storageSplitInterface.getPartitions() != null) {
                splited = StorageService.partition(runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, runContext.render(storageSplitInterface.getPartitions()).as(Integer.class).orElseThrow());
            } else if (storageSplitInterface.getRows() != null) {
                Integer renderedRows = runContext.render(storageSplitInterface.getRows()).as(Integer.class).orElseThrow();
                splited = StorageService.split(runContext, extension, runContext.render(storageSplitInterface.getSeparator()).as(String.class).orElseThrow(),
                    bufferedReader, (bytes, size) -> size >= renderedRows);
            } else {
                throw new IllegalArgumentException("Invalid configuration with no size, count, rows, nor regexPattern");
            }

            return splited
                .stream()
                .map(throwFunction(path -> runContext.storage().putFile(path.toFile())))
                .toList();
        }
    }

    private static List<Path> split(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, BiFunction<Integer, Integer, Boolean> predicate) throws IOException {
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

    private static List<Path> partition(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, int partition) throws IOException {
        List<Path> files = new ArrayList<>();
        List<RandomAccessFile> writers = new ArrayList<>();

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

        writers.forEach(throwConsumer(RandomAccessFile::close));

        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

    private static List<Path> splitByRegex(RunContext runContext, String extension, String separator, BufferedReader bufferedReader, String regexPattern) throws IOException {
        List<Path> files = new ArrayList<>();
        Map<String, RandomAccessFile> writers = new HashMap<>();
        Pattern pattern = Pattern.compile(regexPattern);
        
        String row;
        while ((row = bufferedReader.readLine()) != null) {
            Matcher matcher = pattern.matcher(row);
            
            if (matcher.find() && matcher.groupCount() > 0) {
                String routingKey = matcher.group(1);
                
                // Get or create writer for this routing key
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
            // Lines that don't match the pattern are ignored
        }
        
        writers.values().forEach(throwConsumer(RandomAccessFile::close));
        
        return files.stream().filter(p -> p.toFile().length() > 0).toList();
    }

}
