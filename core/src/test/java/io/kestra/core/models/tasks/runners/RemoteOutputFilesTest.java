package io.kestra.core.models.tasks.runners;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.WorkingDir;
import io.kestra.core.storages.Storage;
import io.kestra.core.utils.IdUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RemoteOutputFilesTest {
    private static final Path WORKING_DIR = Path.of("/tmp/kestra-remote-output-files-test");

    @Test
    void shouldMatchFilesServiceOutputFilesKeyAndName() throws IOException {
        RunContext runContext = mockRunContext();
        Storage storage = runContext.storage();
        when(storage.putFile(any(InputStream.class), anyString())).thenAnswer(invocation -> {
            ((InputStream) invocation.getArgument(0)).close();
            return URI.create("kestra:///" + invocation.getArgument(1, String.class));
        });

        var staged = new RemoteOutputFiles.StagedOutput("result.txt", null, () -> new ByteArrayInputStream("hello".getBytes()));

        var result = RemoteOutputFiles.collect(runContext, List.of(staged), List.of("result.txt"), false, null);

        assertThat(result.outputFiles()).containsEntry("result.txt", URI.create("kestra:///" + uniqueName("result.txt")));
        assertThat(result.streamed()).isEqualTo(1);
        assertThat(result.copied()).isZero();
        verify(storage, never()).copyFrom(anyString(), any());
    }

    @Test
    void shouldMatchScriptServiceUploadOutputFilesKeyAndNameForOutputDirEntries() throws IOException {
        RunContext runContext = mockRunContext();
        Storage storage = runContext.storage();
        when(storage.putFile(any(InputStream.class), anyString())).thenAnswer(invocation -> {
            ((InputStream) invocation.getArgument(0)).close();
            return URI.create("kestra:///" + invocation.getArgument(1, String.class));
        });

        var staged = new RemoteOutputFiles.StagedOutput("outdir/result.txt", null, () -> new ByteArrayInputStream("hello".getBytes()));

        var result = RemoteOutputFiles.collect(runContext, List.of(staged), null, true, "outdir");

        // unlike outputFiles-pattern entries, an outputDir entry keeps its plain relative filename, with no unique-id prefix
        assertThat(result.outputFiles()).containsEntry("result.txt", URI.create("kestra:///result.txt"));
    }

    @Test
    void shouldExcludeExecutionContextFile() throws IOException {
        RunContext runContext = mockRunContext();

        var staged = new RemoteOutputFiles.StagedOutput(WorkingDir.EXECUTION_CONTEXT_FILE_NAME, null, InputStream::nullInputStream);

        var result = RemoteOutputFiles.collect(runContext, List.of(staged), List.of(WorkingDir.EXECUTION_CONTEXT_FILE_NAME), false, null);

        assertThat(result.outputFiles()).isEmpty();
        assertThat(result.copied()).isZero();
        assertThat(result.streamed()).isZero();
        verifyNoInteractions(runContext.storage());
    }

    @Test
    void shouldCopyServerSideWhenAvailableAndStreamOtherwise() throws IOException {
        RunContext runContext = mockRunContext();
        Storage storage = runContext.storage();

        URI copyableProviderUri = URI.create("s3://bucket/copy-me.txt");
        URI streamedProviderUri = URI.create("s3://bucket/stream-me.txt");

        when(storage.copyFrom(eq(uniqueName("copy.txt")), eq(copyableProviderUri))).thenReturn(Optional.of(URI.create("kestra:///copied")));
        when(storage.copyFrom(eq(uniqueName("stream.txt")), eq(streamedProviderUri))).thenReturn(Optional.empty());
        when(storage.putFile(any(InputStream.class), eq(uniqueName("stream.txt")))).thenAnswer(invocation -> {
            ((InputStream) invocation.getArgument(0)).close();
            return URI.create("kestra:///streamed");
        });

        List<RemoteOutputFiles.StagedOutput> staged = List.of(
            new RemoteOutputFiles.StagedOutput("copy.txt", copyableProviderUri, () -> {
                throw new AssertionError("must not stream when the server-side copy already succeeded");
            }),
            new RemoteOutputFiles.StagedOutput("stream.txt", streamedProviderUri, () -> new ByteArrayInputStream("hi".getBytes()))
        );

        var result = RemoteOutputFiles.collect(runContext, staged, List.of("*.txt"), false, null);

        assertThat(result.copied()).isEqualTo(1);
        assertThat(result.streamed()).isEqualTo(1);
        assertThat(result.outputFiles()).containsEntry("copy.txt", URI.create("kestra:///copied"));
        assertThat(result.outputFiles()).containsEntry("stream.txt", URI.create("kestra:///streamed"));
    }

    private static RunContext mockRunContext() {
        RunContext runContext = mock(RunContext.class);
        WorkingDir workingDir = mock(WorkingDir.class);
        when(workingDir.path()).thenReturn(WORKING_DIR);
        when(runContext.workingDir()).thenReturn(workingDir);
        when(runContext.storage()).thenReturn(mock(Storage.class));
        return runContext;
    }

    private static String uniqueName(String relativePath) {
        return IdUtils.from(WORKING_DIR.resolve(relativePath).toString()) + "-" + relativePath;
    }
}
