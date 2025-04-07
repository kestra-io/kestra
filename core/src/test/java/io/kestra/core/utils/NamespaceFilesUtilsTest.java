package io.kestra.core.utils;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.NamespaceFile;
import io.kestra.core.storages.StorageInterface;
import io.kestra.plugin.core.log.Log;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class NamespaceFilesUtilsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    QueueInterface<LogEntry> workerTaskLogQueue;

    @Inject
    NamespaceFilesUtils namespaceFilesUtils;

    @Test
    void defaultNs() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, Collections.emptyMap());
        String namespace = runContext.flowInfo().namespace();

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 100; i++) {
            storageInterface.put(null, namespace, toNamespacedStorageUri(namespace, URI.create("/" + i + ".txt")), data);
        }

        namespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder().build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        assertThat(logEntry.getFirst().getMessage(), containsString("Loaded 100 namespace files"));
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue(), is(100D));
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue(), isA(Duration.class));
    }

    @Test
    void customNs() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue, either -> logs.add(either.getLeft()));

        Log task = Log.builder().id(IdUtils.create()).type(Log.class.getName()).message("Yo!").build();
        var runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());
        String namespace = IdUtils.create();

        ByteArrayInputStream data = new ByteArrayInputStream("a".repeat(1024).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < 100; i++) {
            storageInterface.put(null, namespace, toNamespacedStorageUri(namespace, URI.create("/" + i + ".txt")), data);
        }

        namespaceFilesUtils.loadNamespaceFiles(runContext, NamespaceFiles.builder().namespaces(Property.of(List.of(namespace))).build());

        List<LogEntry> logEntry = TestsUtils.awaitLogs(logs, 1);
        receive.blockLast();

        assertThat(logEntry.getFirst().getMessage(), containsString("Loaded 100 namespace files"));
        assertThat(runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.count")).findFirst().orElseThrow().getValue(), is(100D));
        assertThat((Duration) runContext.metrics().stream().filter(m -> m.getName().equals("namespacefiles.duration")).findFirst().orElseThrow().getValue(), isA(Duration.class));
    }

    private URI toNamespacedStorageUri(String namespace, @Nullable URI relativePath) {
        return NamespaceFile.of(namespace, relativePath).storagePath().toUri();
    }
}