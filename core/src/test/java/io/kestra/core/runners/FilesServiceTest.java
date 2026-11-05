package io.kestra.core.runners;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.Storage;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest(rebuildContext = true)
@Execution(ExecutionMode.SAME_THREAD)
class FilesServiceTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private NamespaceFactory namespaceFactory;

    @Test
    void shouldRejectPathTraversalInInputFileNames() {
        RunContext runContext = runContextFactory.of();
        assertThatThrownBy(
            () -> FilesService.inputFiles(runContext, Map.of("../escape.txt", "content"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAbsolutePathInInputFileNames() {
        RunContext runContext = runContextFactory.of();
        assertThatThrownBy(
            () -> FilesService.inputFiles(runContext, Map.of("/etc/passwd", "content"))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overrideExistingInputFile() throws Exception {
        RunContext runContext = runContextFactory.of();
        FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        FilesService.inputFiles(runContext, Map.of("file.txt", "overridden content"));

        String fileContent = FileUtils.readFileToString(runContext.workingDir().path().resolve("file.txt").toFile(), "UTF-8");
        assertThat(fileContent).isEqualTo("overridden content");
    }

    @Test
    void renderInputFile() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("filename", "file.txt", "content", "Hello World"));
        Map<String, String> content = FilesService.inputFiles(runContext, Map.of("{{filename}}", "{{content}}"));
        assertThat(content.get("file.txt")).isEqualTo("Hello World");
    }

    @Test
    void renderRawFile() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("filename", "file.txt", "content", "Hello World"));
        Map<String, String> content = FilesService.inputFiles(runContext, Map.of("{{filename}}", "{% raw %}{{content}}{% endraw %}"));
        assertThat(content.get("file.txt")).isEqualTo("{{content}}");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void localFileAsInputFile() throws Exception {
        URI uri = createFile();
        RunContext runContext = runContextFactory.of();
        ((DefaultRunContext) runContext).init(applicationContext);
        FilesService.inputFiles(runContext, Map.of("file.txt", uri.toString()));
        Path file = runContext.workingDir().resolve(Path.of("file.txt"));
        assertThat(new String(Files.readAllBytes(file))).isEqualTo("Hello World");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void nsFileAsInputFile() throws Exception {
        URI uri = createNsFile(false);
        RunContext runContext = runContextFactory.of();
        FilesService.inputFiles(runContext, Map.of("file.txt", uri.toString()));
        Path file = runContext.workingDir().resolve(Path.of("file.txt"));
        assertThat(new String(Files.readAllBytes(file))).isEqualTo("Hello World");
    }

    @Test
    void outputFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        Map<String, URI> outputs = FilesService.outputFiles(runContext, files.keySet().stream().toList());
        assertThat(outputs.size()).isEqualTo(1);
    }

    @Test
    void shouldRejectAnnouncementsWhenStagedFileIdentityIsUnavailable() throws Exception {
        var context = Mockito.spy(runContextFactory.of());
        Storage storage = Mockito.mock(Storage.class);
        Mockito.doReturn(storage).when(context).storage();
        var generated = Files.writeString(context.workingDir().path().resolve("generated.ion"), "{id:1}");
        Map<Path, Object> staged = new java.util.HashMap<>();
        staged.put(context.workingDir().path().resolve("credential.txt"), null);

        assertThatThrownBy(() -> FilesService.outputFiles(context, List.of(), List.of("generated.ion"), null, new java.util.HashMap<>(), staged))
            .isInstanceOf(IOException.class).hasMessageContaining("filesystem identities");
        Mockito.verifyNoInteractions(storage);
        assertThat(Files.exists(generated)).isTrue();
    }

    @Test
    void shouldCollectAnnouncedFilesWithAnUnnormalizedWorkingDirectory(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("nested"));
        var workingDir = new LocalWorkingDir(tempDir.resolve("nested/.."), "task");
        var runContext = Mockito.spy(runContextFactory.of());
        Mockito.doReturn(workingDir).when(runContext).workingDir();
        Files.writeString(workingDir.path().resolve("record.ion"), "{id:1}");

        var outputs = FilesService.outputFiles(runContext, List.of(), List.of("./record.ion"));

        assertThat(outputs).containsOnlyKeys("record.ion");
        try (var input = runContext.storage().getFile(outputs.get("record.ion"))) {
            assertThat(new String(input.readAllBytes())).isEqualTo("{id:1}");
        }
    }

    @Test
    void shouldRejectAnnouncedFilesThroughSymlinkedDirectories(@TempDir Path outside) throws Exception {
        var runContext = runContextFactory.of();
        Files.writeString(outside.resolve("record.ion"), "{id:1}");
        Files.createSymbolicLink(runContext.workingDir().path().resolve("escape"), outside);

        assertThatThrownBy(() -> FilesService.outputFiles(runContext, List.of(), List.of("escape/record.ion")))
            .isInstanceOf(IOException.class);
    }

    @Test
    void shouldRejectAnAnnouncedFileWhenTheWorkingDirectoryIsReplacedByASymlink(@TempDir Path tempDir) throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("record.ion"), "{id:1}");
        Files.createSymbolicLink(tempDir.resolve("task"), outside);
        var runContext = Mockito.spy(runContextFactory.of());
        Mockito.doReturn(new LocalWorkingDir(tempDir, "task")).when(runContext).workingDir();

        assertThatThrownBy(() -> FilesService.outputFiles(runContext, List.of(), List.of("record.ion")))
            .isInstanceOf(IOException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldRejectReplacementBeforeOpeningAnnouncedFile(boolean replaceParent, @TempDir Path outside) throws Exception {
        var runContext = Mockito.spy(runContextFactory.of());
        Path root = runContext.workingDir().path();
        Files.writeString(root.resolve("first.ion"), "safe");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub/record.ion"), "safe");
        Files.writeString(outside.resolve("record.ion"), "secret");
        Storage storage = Mockito.mock(Storage.class);
        Mockito.doReturn(storage).when(runContext).storage();
        var captured = new ArrayList<String>();
        Answer<URI> upload = invocation ->
        {
            Object source = invocation.getArgument(0);
            try (InputStream input = source instanceof File file ? new FileInputStream(file) : (InputStream) source) {
                captured.add(new String(input.readAllBytes()));
            }
            if (captured.size() == 1) {
                Path target = root.resolve(replaceParent ? "sub" : "sub/record.ion");
                Files.move(target, root.resolve("original"));
                Files.createSymbolicLink(target, replaceParent ? outside : outside.resolve("record.ion"));
            }
            return URI.create("kestra:///captured/" + captured.size());
        };
        Mockito.when(storage.putFile(Mockito.any(File.class), Mockito.anyString())).thenAnswer(upload);
        Mockito.when(storage.putFile(Mockito.any(InputStream.class), Mockito.anyString())).thenAnswer(upload);

        assertThatThrownBy(() -> FilesService.outputFiles(runContext, List.of(), List.of("first.ion", "sub/record.ion")))
            .isInstanceOf(IOException.class);
        assertThat(captured).containsExactly("safe");
        assertThat(Files.readString(outside.resolve("record.ion"))).isEqualTo("secret");
    }

    @Test
    void shouldKeepTheOpenFileAndCleanupInsideThePinnedParent(@TempDir Path outside) throws Exception {
        var context = Mockito.spy(runContextFactory.of());
        Path root = context.workingDir().path();
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub/record.ion"), "safe");
        Files.writeString(outside.resolve("record.ion"), "secret");
        Storage storage = Mockito.mock(Storage.class);
        Mockito.doReturn(storage).when(context).storage();
        var streams = new ArrayList<InputStream>();
        Mockito.when(storage.putFile(Mockito.any(InputStream.class), Mockito.anyString())).thenAnswer(invocation ->
        {
            InputStream input = invocation.getArgument(0);
            streams.add(input);
            Files.move(root.resolve("sub"), root.resolve("original"));
            Files.createSymbolicLink(root.resolve("sub"), outside);
            assertThat(new String(input.readAllBytes())).isEqualTo("safe");
            return URI.create("kestra:///record.ion");
        });

        var output = FilesService.outputFiles(context, List.of(), List.of("sub/record.ion"));

        assertThat(output).containsEntry("sub/record.ion", URI.create("kestra:///record.ion"));
        assertThat(Files.exists(root.resolve("original/record.ion"))).isFalse();
        assertThat(Files.readString(outside.resolve("record.ion"))).isEqualTo("secret");
        assertThatThrownBy(() -> streams.getFirst().read()).isInstanceOf(IOException.class);
        Mockito.verify(storage, Mockito.never()).putFile(Mockito.any(File.class), Mockito.anyString());
    }

    @Test
    void shouldFailClosedOnFilesystemsWithoutSecureDirectoryStreams(@TempDir Path tempDir) throws Exception {
        try (var fs = java.nio.file.FileSystems.newFileSystem(URI.create("jar:" + tempDir.resolve("files.zip").toUri()), Map.of("create", "true"))) {
            Path root = Files.createDirectory(fs.getPath("/task"));
            Files.writeString(root.resolve("record.ion"), "safe");
            var workingDir = Mockito.mock(WorkingDir.class);
            Mockito.when(workingDir.path()).thenReturn(root);
            Mockito.when(workingDir.resolve(Mockito.any())).thenAnswer(invocation -> root.resolve(invocation.<Path> getArgument(0).toString()));
            var context = Mockito.spy(runContextFactory.of());
            Mockito.doReturn(workingDir).when(context).workingDir();
            Storage storage = Mockito.mock(Storage.class);
            Mockito.doReturn(storage).when(context).storage();

            assertThatThrownBy(() -> FilesService.outputFiles(context, List.of(), List.of("record.ion")))
                .isInstanceOf(IOException.class).hasMessageContaining("SecureDirectoryStream");
            Mockito.verifyNoInteractions(storage);
            assertThat(Files.readString(root.resolve("record.ion"))).isEqualTo("safe");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void shouldUploadFilesystemAliasesOnce(boolean caseAlias) throws Exception {
        var context = runContextFactory.of();
        Path original = context.workingDir().path().resolve("Record.ion");
        Path alias = context.workingDir().path().resolve(caseAlias ? "record.ion" : "linked.ion");
        Files.writeString(original, "{id:1}");
        if (caseAlias) {
            org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(alias), "Requires a case-insensitive filesystem");
        } else {
            Files.createLink(alias, original);
        }
        assertThat(Files.isSameFile(original, alias)).isTrue();

        var output = FilesService.outputFiles(context, List.of("Record.ion"), List.of(alias.getFileName().toString()));

        assertThat(output).containsOnlyKeys("Record.ion", alias.getFileName().toString());
        assertThat(output.get("Record.ion")).isEqualTo(output.get(alias.getFileName().toString()));
        assertThat(Files.exists(original)).isFalse();
        assertThat(Files.exists(alias)).isFalse();
        try (var input = context.storage().getFile(output.get("Record.ion"))) {
            assertThat(new String(input.readAllBytes())).isEqualTo("{id:1}");
        }
    }

    @Test
    void shouldReuseDirectoryOutputUriForAHardLinkWithoutAcceptingSymlinkAliases() throws Exception {
        var context = runContextFactory.of();
        Path root = context.workingDir().path();
        Path directory = Files.createDirectory(root.resolve("out"));
        Path original = Files.writeString(directory.resolve("Record.ion"), "{id:1}");
        Path alias = Files.createLink(root.resolve("alias.ion"), original);
        var uploaded = new java.util.HashMap<String, URI>();
        FilesService.outputFiles(context, List.of("out/*.ion"), List.of("alias.ion"), directory, uploaded);
        assertThat(uploaded).containsOnlyKeys("Record.ion", "out/Record.ion", "alias.ion");
        assertThat(uploaded.values()).containsOnly(uploaded.get("Record.ion"));
        assertThat(uploaded.get("Record.ion").getPath()).endsWith("/Record.ion");
        assertThat(Files.exists(original)).isFalse();
        assertThat(Files.exists(alias)).isFalse();

        Files.writeString(original, "{id:2}");
        Files.createSymbolicLink(alias, original);
        assertThatThrownBy(() -> FilesService.outputFiles(context, List.of(), List.of("alias.ion"), directory, new java.util.HashMap<>()))
            .isInstanceOf(IOException.class).hasMessageContaining("symbolic links");
    }

    @Test
    void renderOutputFiles() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("extension", "txt"));
        Map<String, String> files = FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        Map<String, URI> outputs = FilesService.outputFiles(runContext, List.of("*.{{extension}}"));
        assertThat(outputs.size()).isEqualTo(1);
    }

    @Test
    void testOutputFilesWithSpecialCharacters(@TempDir Path tempDir) throws Exception {
        var runContext = runContextFactory.of();

        Path fileWithSpace = tempDir.resolve("with space.txt");
        Path fileWithUnicode = tempDir.resolve("สวัสดี&.txt");

        Files.writeString(fileWithSpace, "content");
        Files.writeString(fileWithUnicode, "content");

        Path targetFileWithSpace = runContext.workingDir().path().resolve("with space.txt");
        Path targetFileWithUnicode = runContext.workingDir().path().resolve("สวัสดี&.txt");

        Files.copy(fileWithSpace, targetFileWithSpace);
        Files.copy(fileWithUnicode, targetFileWithUnicode);

        Map<String, URI> outputFiles = FilesService.outputFiles(
            runContext,
            List.of("with space.txt", "สวัสดี&.txt")
        );

        assertThat(outputFiles).hasSize(2);
        assertThat(outputFiles).containsKey("with space.txt");
        assertThat(outputFiles).containsKey("สวัสดี&.txt");

        assertThat(runContext.storage().getFile(outputFiles.get("with space.txt"))).isNotNull();
        assertThat(runContext.storage().getFile(outputFiles.get("สวัสดี&.txt"))).isNotNull();
    }

    private URI createFile() throws IOException {
        File tempFile = Files.createTempFile(Path.of("/tmp"), "file", ".txt").toFile();
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI createNsFile(boolean nsInAuthority) throws IOException, URISyntaxException {
        String namespace = "namespace";
        String filePath = "file.txt";
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
