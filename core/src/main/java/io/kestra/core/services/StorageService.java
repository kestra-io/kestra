package io.kestra.core.services;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.StorageSplitInterface;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static io.kestra.core.utils.Rethrow.*;

public abstract class StorageService {

    public static List<URI> split(RunContext runContext, StorageSplitInterface storageSplitInterface, URI from) throws IOException, IllegalVariableEvaluationException {
        String fromPath = from.getPath();
        String extension = ".tmp";
        if (fromPath.indexOf('.') >= 0) {
            extension = fromPath.substring(fromPath.lastIndexOf('.'));
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runContext.storage().getFile(from)))) {
            List<Path> splited;

            if (storageSplitInterface.getBytes() != null) {
                ReadableBytesTypeConverter readableBytesTypeConverter = new ReadableBytesTypeConverter();
                Number convert = readableBytesTypeConverter.convert(storageSplitInterface.getBytes().as(runContext, String.class), Number.class)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid size with value '" + storageSplitInterface.getBytes() + "'"));

                splited = StorageService.split(runContext, extension, runContext.render(storageSplitInterface.getSeparator(), String.class),
                    bufferedReader, (bytes, size) -> bytes >= convert.longValue());
            } else if (storageSplitInterface.getPartitions() != null) {
                splited = StorageService.partition(runContext, extension, runContext.render(storageSplitInterface.getSeparator(), String.class),
                    bufferedReader, storageSplitInterface.getPartitions().as(runContext, Integer.class));
            } else if (storageSplitInterface.getRows() != null) {
                splited = StorageService.split(runContext, extension, runContext.render(storageSplitInterface.getSeparator(), String.class),
                    bufferedReader, throwBiFunction((bytes, size) -> size >= storageSplitInterface.getRows().as(runContext, Integer.class)));
            } else {
                throw new IllegalArgumentException("Invalid configuration with no size, count, nor rows");
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

}
