package io.kestra.plugin.core.storage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.google.common.io.CharStreams;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkingDir;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.storages.Storage;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Rethrow;
import io.kestra.worker.processors.internals.WorkerTaskCallable;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@KestraTest
class SplitTest {
    @Inject
    TestRunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Test
    void partition() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .partitions(Property.ofValue(8))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(8);
        assertThat(StorageContext.logicalPath(run.getUris().getFirst())).endsWith(".yml");
        assertThat(StringUtils.countMatches(readAll(run.getUris()), "\n")).isEqualTo(1000);
    }

    @Test
    void rows() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(1000);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .rows(Property.ofValue(10))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(100);
        assertThat(readAll(run.getUris())).isEqualTo(String.join("\n", content(1000)) + "\n");
    }

    @Test
    void bytes() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUpload(12288);

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .bytes(Property.ofValue("1KB"))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(251);
        assertThat(readAll(run.getUris())).isEqualTo(String.join("\n", content(12288)) + "\n");
    }

    @Test
    void regexPattern() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadWithRegexContent();

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .regexPattern(Property.ofValue("\\[(\\w+)\\]"))
            .build();

        Split.Output run = result.run(runContext);
        assertThat(run.getUris().size()).isEqualTo(3);

        String allContent = readAll(run.getUris());
        assertThat(allContent).contains("[ERROR] Error message 1");
        assertThat(allContent).contains("[WARN] Warning message 1");
        assertThat(allContent).contains("[INFO] Info message 1");
        assertThat(allContent).contains("[ERROR] Error message 2");
    }

    @Test
    void partitionIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .partitions(Property.ofValue(8))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(8);
        assertThat(StorageContext.logicalPath(run.getUris().getFirst())).endsWith(".ion");
        assertThat(readAllIon(run.getUris())).hasSize(1000);
    }

    @Test
    void shouldKeepIonExtensionWhenTheSourceIsASingleSegmentKestraUri() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageInterface.put(
            MAIN_TENANT,
            null,
            URI.create("/report.ion"),
            new FileInputStream(ionFile(ionContent(2)))
        );
        assertThat(put).isEqualTo(URI.create("kestra://report.ion"));

        Split.Output run = Split.builder()
            .from(Property.ofValue(put.toString()))
            .rows(Property.ofValue(1))
            .build()
            .run(runContext);

        assertThat(run.getUris()).hasSize(2);
        assertThat(run.getUris()).allSatisfy(uri -> assertThat(StorageContext.logicalPath(uri)).endsWith(".ion"));
    }

    @Test
    void rowsIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .rows(Property.ofValue(10))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isEqualTo(100);

        List<Object> records = readAllIon(run.getUris());
        assertThat(records).hasSize(1000);
        // order is preserved across the split
        List<Integer> ids = records.stream().map(record -> ((Number) ((Map<?, ?>) record).get("id")).intValue()).toList();
        assertThat(ids).isEqualTo(IntStream.range(0, 1000).boxed().toList());
    }

    @Test
    void bytesIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(ionContent(1000));

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .bytes(Property.ofValue("1KB"))
            .build();

        Split.Output run = result.run(runContext);

        assertThat(run.getUris().size()).isGreaterThan(1);
        assertThat(readAllIon(run.getUris())).hasSize(1000);
    }

    @Test
    void regexPatternIon() throws Exception {
        RunContext runContext = runContextFactory.of();
        URI put = storageUploadIon(
            List.of(
                Map.of("id", 1, "level", "ERROR"),
                Map.of("id", 2, "level", "WARN"),
                Map.of("id", 3, "level", "INFO"),
                Map.of("id", 4, "level", "ERROR"),
                Map.of("id", 5, "level", "WARN"),
                Map.of("id", 6, "level", "INFO"),
                Map.of("id", 7),
                Map.of("id", 8, "level", "ERROR")
            )
        );

        Split result = Split.builder()
            .from(Property.ofValue(put.toString()))
            .regexPattern(Property.ofValue("level:\"(\\w+)\""))
            .build();

        Split.Output run = result.run(runContext);
        // one file per distinct level (ERROR, WARN, INFO); the record without a level is dropped
        assertThat(run.getUris().size()).isEqualTo(3);

        List<Object> records = readAllIon(run.getUris());
        assertThat(records).hasSize(7);
        List<String> levels = records.stream().map(record -> (String) ((Map<?, ?>) record).get("level")).toList();
        assertThat(levels).containsOnly("ERROR", "WARN", "INFO");
    }

    @ParameterizedTest
    @MethodSource("splitCases")
    void shouldFailWithoutPublishingWhenFinalWriteFails(SplitMode mode, Format format) throws Exception {
        SplitFixture fixture = splitFixture(mode, format, 9);
        IOException writeFailure = new IOException("Cannot write the final split records.");
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (var outputs = mockOutputs(fixture, writers, index -> index == 0 ? writeFailure : null, index -> null)) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isInstanceOf(IOException.class);
        assertThat(writers.getFirst().writes).isPositive();
        assertWritersClosed(writers, 3);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldCloseAllWritersAndSuppressFailuresWhenMultipleFinalWritesFail(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 9);
        List<IOException> failures = IntStream.range(0, 3)
            .mapToObj(index -> new IOException("Cannot finalize split writer %d.".formatted(index)))
            .toList();
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (var outputs = mockOutputs(fixture, writers, failures::get, index -> null)) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isInstanceOf(IOException.class).isIn(failures);
        assertThat(failure.getSuppressed()).containsExactlyInAnyOrderElementsOf(
            failures.stream().filter(exception -> exception != failure).toList()
        );
        assertWritersClosed(writers, 3);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldPreserveFailureWhenWritersThrowTheSameException(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 9);
        IOException writeFailure = new IOException("Cannot write the final split records.");
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (var outputs = mockOutputs(fixture, writers, index -> writeFailure, index -> null)) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isSameAs(writeFailure);
        assertThat(failure.getSuppressed()).isEmpty();
        assertWritersClosed(writers, 3);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldPreserveWriteFailureWhenCleanupAlsoFails(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 9);
        Split task = Split.builder()
            .from(fixture.task().getFrom())
            .partitions(fixture.task().getPartitions())
            .regexPattern(fixture.task().getRegexPattern())
            .separator(Property.ofValue("x".repeat(FileSerde.BUFFER_SIZE)))
            .build();
        IOException writeFailure = new IOException("Cannot write a split record.");
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (
            var outputs = mockOutputs(
                fixture, writers, index -> writeFailure,
                index -> new IOException("Cannot close split writer %d.".formatted(index))
            )
        ) {
            failure = catchThrowable(() -> task.run(fixture.runContext()));
        }

        assertThat(failure).isSameAs(writeFailure);
        assertThat(failure.getSuppressed()).containsExactlyInAnyOrderElementsOf(
            writers.stream().map(writer -> writer.closeFailure).toList()
        );
        assertWritersClosed(writers, mode == SplitMode.PARTITIONS ? 3 : 1);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldPreserveReadFailureWhenCleanupAlsoFails(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 9);
        UncheckedIOException readFailure = new UncheckedIOException(new IOException("Cannot read the split input."));
        doAnswer(invocation -> new FilterInputStream((InputStream) invocation.callRealMethod()) {
            private boolean read;

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                if (read) {
                    throw readFailure;
                }
                read = true;
                return super.read(bytes, offset, length);
            }

            @Override
            public int available() {
                return 0;
            }
        }).when(fixture.storage()).getFile(fixture.source());
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (
            var outputs = mockOutputs(
                fixture, writers, index -> null,
                index -> new IOException("Cannot close split writer %d.".formatted(index))
            )
        ) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isSameAs(readFailure);
        assertThat(failure.getSuppressed()).containsExactlyInAnyOrderElementsOf(
            writers.stream().map(writer -> writer.closeFailure).toList()
        );
        assertWritersClosed(writers, 3);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldCloseRegisteredWritersWhenCreatingAnotherWriterFails(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 9);
        WorkingDir workingDir = spy(fixture.runContext().workingDir());
        doReturn(workingDir).when(fixture.runContext()).workingDir();
        IOException creationFailure = new IOException("Cannot create another split file.");
        int[] createdFiles = { 0 };
        doAnswer(invocation ->
        {
            if (++createdFiles[0] == 2) {
                throw creationFailure;
            }
            return invocation.callRealMethod();
        }).when(workingDir).createTempFile(".txt");
        IOException closeFailure = new IOException("Cannot close the first split writer.");
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (var outputs = mockOutputs(fixture, writers, index -> null, index -> closeFailure)) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isSameAs(creationFailure);
        assertThat(failure.getSuppressed()).containsExactly(closeFailure);
        assertWritersClosed(writers, 1);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @MethodSource("splitCases")
    void shouldFailWithoutPublishingWhenCloseFailsAfterFlush(SplitMode mode, Format format) throws Exception {
        SplitFixture fixture = splitFixture(mode, format, 9);
        IOException closeFailure = new IOException("Cannot close a flushed split writer.");
        List<WriterFault> writers = new ArrayList<>();
        Throwable failure;

        try (var outputs = mockOutputs(fixture, writers, index -> null, index -> index == 0 ? closeFailure : null)) {
            failure = catchThrowable(() -> fixture.task().run(fixture.runContext()));
        }

        assertThat(failure).isInstanceOf(IOException.class);
        assertWritersClosed(writers, 3);
        for (WriterFault writer : writers) {
            assertThat(Files.size(writer.path)).isPositive();
        }
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @Test
    void shouldReportFailedWorkerWhenFinalWriteFails() throws Exception {
        SplitFixture fixture = splitFixture(SplitMode.PARTITIONS, Format.TEXT, 9);
        WorkerTask workerTask = WorkerTask.builder()
            .task(fixture.task())
            .taskRun(TaskRun.builder().id("split-finalization").build())
            .build();
        WorkerTaskCallable callable = new WorkerTaskCallable(workerTask, fixture.task(), fixture.runContext(), mock(MetricRegistry.class), null);
        IOException writeFailure = new IOException("Cannot write the final split records.");
        List<WriterFault> writers = new ArrayList<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        State.Type state;

        try (var outputs = mockOutputs(fixture, writers, index -> index == 0 ? writeFailure : null, index -> null)) {
            state = callable.call();
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }

        assertThat(state).isEqualTo(State.Type.FAILED);
        assertThat(callable.getException()).isSameAs(writeFailure);
        assertThat(callable.getTaskOutput()).isNull();
        assertWritersClosed(writers, 3);
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(SplitMode.class)
    void shouldReturnEmptyOutputWhenInputIsEmpty(SplitMode mode) throws Exception {
        SplitFixture fixture = splitFixture(mode, Format.TEXT, 0);

        assertThat(fixture.task().run(fixture.runContext()).getUris()).isEmpty();
        assertNoPublicationAndSourcePreserved(fixture);
    }

    @ParameterizedTest
    @EnumSource(Format.class)
    void shouldReturnEmptyOutputWhenNoRecordsMatchRegex(Format format) throws Exception {
        SplitFixture fixture = splitFixture(SplitMode.REGEX, format, 9);
        Split task = Split.builder()
            .from(fixture.task().getFrom())
            .regexPattern(Property.ofValue("not-present"))
            .build();

        assertThat(task.run(fixture.runContext()).getUris()).isEmpty();
        assertNoPublicationAndSourcePreserved(fixture);
    }

    private static Stream<Arguments> splitCases() {
        return Stream.of(SplitMode.values())
            .flatMap(mode -> Stream.of(Format.values()).map(format -> Arguments.of(mode, format)));
    }

    private SplitFixture splitFixture(SplitMode mode, Format format, int count) throws Exception {
        URI source;
        if (format == Format.ION) {
            source = storageUploadIon(
                IntStream.range(0, count)
                    .mapToObj(index -> Map.<String, Object> of("id", index, "group", "g%d".formatted(index % 3)))
                    .toList()
            );
        } else {
            Path path = Files.createTempFile("split-input-", ".txt");
            Files.write(
                path, IntStream.range(0, count)
                    .mapToObj(index -> "[g%d] record %d".formatted(index % 3, index))
                    .toList()
            );
            try (InputStream input = Files.newInputStream(path)) {
                source = storageInterface.put(
                    MAIN_TENANT, null,
                    URI.create("/file/storage/%s/input.txt".formatted(IdUtils.create())), input
                );
            } finally {
                Files.deleteIfExists(path);
            }
        }

        byte[] sourceBytes;
        try (InputStream input = storageInterface.get(MAIN_TENANT, null, source)) {
            sourceBytes = input.readAllBytes();
        }
        RunContext runContext = spy(runContextFactory.of());
        Storage storage = spy(runContext.storage());
        doReturn(storage).when(runContext).storage();
        Split.SplitBuilder<?, ?> builder = Split.builder().id("split-finalization").from(Property.ofValue(source.toString()));
        switch (mode) {
            case PARTITIONS -> builder.partitions(Property.ofValue(3));
            case REGEX -> builder.regexPattern(Property.ofValue(format == Format.ION ? "group:\"(g\\d)\"" : "\\[(g\\d)\\]"));
        }
        return new SplitFixture(builder.build(), runContext, storage, source, sourceBytes);
    }

    private MockedConstruction<FileOutputStream> mockOutputs(SplitFixture fixture, List<WriterFault> writers,
        IntFunction<IOException> writeFailure, IntFunction<IOException> closeFailure) {
        Path workingDir = fixture.runContext().workingDir().path().toAbsolutePath().normalize();
        return mockConstruction(FileOutputStream.class, (stream, context) ->
        {
            Object destination = context.arguments().getFirst();
            Path path = (destination instanceof File file ? file.toPath() : Path.of((String) destination)).toAbsolutePath().normalize();
            OutputStream delegate = Files.newOutputStream(path);
            if (path.startsWith(workingDir)) {
                WriterFault writer = new WriterFault(path, delegate, writeFailure.apply(writers.size()), closeFailure.apply(writers.size()));
                writers.add(writer);
                delegate = writer;
            }
            OutputStream output = delegate;
            doAnswer(invocation ->
            {
                output.write(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
                return null;
            }).when(stream).write(any(byte[].class), anyInt(), anyInt());
            doAnswer(invocation ->
            {
                output.write((byte[]) invocation.getArgument(0));
                return null;
            }).when(stream).write(any(byte[].class));
            doAnswer(invocation ->
            {
                output.write((Integer) invocation.getArgument(0));
                return null;
            }).when(stream).write(anyInt());
            doAnswer(invocation ->
            {
                output.flush();
                return null;
            }).when(stream).flush();
            doAnswer(invocation ->
            {
                output.close();
                return null;
            }).when(stream).close();
        });
    }

    private void assertWritersClosed(List<WriterFault> writers, int count) {
        assertThat(writers).hasSize(count).allMatch(writer -> writer.closes > 0);
    }

    private void assertNoPublicationAndSourcePreserved(SplitFixture fixture) throws IOException {
        verify(fixture.storage(), never()).putFile(any(File.class));
        try (InputStream input = storageInterface.get(MAIN_TENANT, null, fixture.source())) {
            assertThat(input.readAllBytes()).isEqualTo(fixture.sourceBytes());
        }
    }

    private List<String> content(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }

    private String readAll(List<URI> uris) throws IOException {
        return uris
            .stream()
            .map(Rethrow.throwFunction(uri -> CharStreams.toString(new InputStreamReader(storageInterface.get(MAIN_TENANT, null, uri)))))
            .collect(Collectors.joining());
    }

    URI storageUpload(int count) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", "");

        Files.write(tempFile.toPath(), content(count));

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.yml".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    URI storageUploadWithRegexContent() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("unit", "");

        List<String> regexContent = List.of(
            "[ERROR] Error message 1",
            "[WARN] Warning message 1",
            "[INFO] Info message 1",
            "[ERROR] Error message 2",
            "[WARN] Warning message 2",
            "[INFO] Info message 2",
            "Line without pattern",
            "[ERROR] Error message 3"
        );

        Files.write(tempFile.toPath(), regexContent);

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.yml".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    private List<Map<String, Object>> ionContent(int count) {
        return IntStream
            .range(0, count)
            .mapToObj(value -> Map.<String, Object> of("id", value))
            .toList();
    }

    URI storageUploadIon(List<Map<String, Object>> records) throws URISyntaxException, IOException {
        File tempFile = ionFile(records);

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/%s/get.ion".formatted(IdUtils.create())),
            new FileInputStream(tempFile)
        );
    }

    private File ionFile(List<Map<String, Object>> records) throws IOException {
        File tempFile = File.createTempFile("unit", ".ion");
        try (
            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
            SequenceWriter writer = FileSerde.createBinarySequenceWriter(outputStream, new TypeReference<Object>() {
            })
        ) {
            for (Map<String, Object> record : records) {
                writer.write(record);
            }
        }
        return tempFile;
    }

    private List<Object> readAllIon(List<URI> uris) throws IOException {
        List<Object> records = new ArrayList<>();
        for (URI uri : uris) {
            try (InputStream inputStream = storageInterface.get(MAIN_TENANT, null, uri)) {
                FileSerde.read(inputStream, records::add);
            }
        }
        return records;
    }

    private enum SplitMode {
        PARTITIONS,
        REGEX
    }

    private enum Format {
        TEXT,
        ION
    }

    private record SplitFixture(Split task, RunContext runContext, Storage storage, URI source, byte[] sourceBytes) {
    }

    private static final class WriterFault extends OutputStream {
        private final Path path;
        private final OutputStream delegate;
        private final IOException writeFailure;
        private final IOException closeFailure;
        private int writes;
        private int closes;

        private WriterFault(Path path, OutputStream delegate, IOException writeFailure, IOException closeFailure) {
            this.path = path;
            this.delegate = delegate;
            this.writeFailure = writeFailure;
            this.closeFailure = closeFailure;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] { (byte) value }, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            writes++;
            if (writeFailure != null) {
                throw writeFailure;
            }
            delegate.write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            closes++;
            delegate.close();
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

}