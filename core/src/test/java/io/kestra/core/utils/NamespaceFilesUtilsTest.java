package io.kestra.core.utils;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.log.Log;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Execution(ExecutionMode.SAME_THREAD)
class NamespaceFilesUtilsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    NamespaceFactory namespaceFactory;

    @Test
    void defaultNs() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Collections.emptyMap());
        String namespace = runContext.flowInfo().namespace();

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        for (int i = 0; i < 100; i++) {
            namespaceStorage.putFile(Path.of("/" + i + ".txt"), data);
        }

        NamespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder().build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        assertThat(logEntry.getFirst().getMessage()).contains("Loaded 100 namespace files");
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue()).isEqualTo(100D);
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue()).isInstanceOf(Duration.class);
    }

    @Test
    void customNs() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        String namespace = IdUtils.create();

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        for (int i = 0; i < 100; i++) {
            namespaceStorage.putFile(Path.of("/" + i + ".txt"), data);
        }

        NamespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder().namespaces(Property.ofValue(List.of(namespace))).build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        assertThat(logEntry.getFirst().getMessage()).contains("Loaded 100 namespace files");
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue()).isEqualTo(100D);
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue()).isInstanceOf(Duration.class);
    }

    @Test
    void multiple_folder_ns() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        String namespace = IdUtils.create();

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/folder1/test.txt"), data);
        namespaceStorage.putFile(Path.of("/folder2/test.txt"), data);
        namespaceStorage.putFile(Path.of("/test.txt"), data);

        NamespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder().namespaces(Property.ofValue(List.of(namespace))).build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        assertThat(logEntry.getFirst().getMessage()).contains("Loaded 3 namespace files");
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue()).isEqualTo(3D);
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue()).isInstanceOf(Duration.class);
    }

    @Test
    void multiple_folder_ns_with_folder_per_ns() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        String baseNs = IdUtils.create();
        String ns1 = baseNs + ".ns1";
        String ns2 = baseNs + ".ns2";

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        namespaceFactory.of(MAIN_TENANT, ns1, storageInterface).putFile(Path.of("/test.txt"), data);
        namespaceFactory.of(MAIN_TENANT, ns2, storageInterface).putFile(Path.of("/test.txt"), data);

        NamespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder()
            .namespaces(Property.ofValue(List.of(ns1, ns2)))
            .folderPerNamespace(Property.ofValue(true))
            .build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        List<Path> persistedFiles = runContext.workingDir().findAllFilesMatching(List.of("regex:.*"));
        assertThat(persistedFiles.size()).isEqualTo(2);
        String stringPaths = persistedFiles.stream().map(Path::toString).collect(Collectors.joining());
        assertThat(stringPaths).contains(ns1 + "/test.txt");
        assertThat(stringPaths).contains(ns2 + "/test.txt");

        assertThat(logEntry.getFirst().getMessage()).contains("Loaded 2 namespace files");
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue()).isEqualTo(2D);
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue()).isInstanceOf(Duration.class);
    }
}
