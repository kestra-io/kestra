package io.kestra.core.utils;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.tasks.FileExistComportment;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.NamespaceFile;

import jakarta.inject.Singleton;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
public class NamespaceFilesUtils {
    private static final int maxThreads = Math.max(Runtime.getRuntime().availableProcessors() * 4, 32);
    private static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(
        0,
        maxThreads,
        60L,
        TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new ThreadFactoryBuilder().setNameFormat("namespace-files").build()
    );

    public void loadNamespaceFiles(
        RunContext runContext,
        NamespaceFiles namespaceFiles)
        throws Exception {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        List<String> include = runContext.render(namespaceFiles.getInclude()).asList(String.class);
        List<String> exclude = runContext.render(namespaceFiles.getExclude()).asList(String.class);
        FileExistComportment fileExistComportment = runContext.render(namespaceFiles.getIfExists())
            .as(FileExistComportment.class).orElse(FileExistComportment.OVERWRITE);
        List<String> namespaces = runContext.render(namespaceFiles.getNamespaces()).asList(String.class);
        Boolean folderPerNamespace = runContext.render(namespaceFiles.getFolderPerNamespace()).as(Boolean.class)
            .orElse(false);

        // Files are loaded in the namespace order, keeping only the latest version of a file: if the same destination
        // path is present in several namespaces, the file from the last namespace wins. As the actual writes below run
        // in parallel, we must deduplicate by destination path beforehand, otherwise the winning file would depend on
        // which concurrent write finishes last (non-deterministic).
        Map<Path, NamespaceFile> matchedNamespaceFiles = new LinkedHashMap<>();
        for (String namespace : namespaces) {
            List<NamespaceFile> files = runContext.storage()
                .namespace(namespace)
                .findAllFilesMatching(include, exclude);

            for (NamespaceFile file : files) {
                matchedNamespaceFiles.put(destinationPath(file, folderPerNamespace), file);
            }
        }

        int parallelism = maxThreads / 2;
        Flux.fromIterable(matchedNamespaceFiles.values())
            .parallel(parallelism)
            .runOn(Schedulers.fromExecutorService(EXECUTOR_SERVICE))
            .doOnNext(throwConsumer(nsFile ->
            {
                try (InputStream content = runContext.storage().getFile(nsFile.uri())) {
                    runContext.workingDir().putFile(destinationPath(nsFile, folderPerNamespace), content, fileExistComportment);
                }
            }))
            .doOnError(t ->
            {
                runContext.logger().error("Error while loading namespace files", t);
            })
            .sequential()
            .blockLast();

        Duration duration = stopWatch.getDuration();

        runContext.metric(Counter.of("namespacefiles.count", matchedNamespaceFiles.size()));
        runContext.metric(Timer.of("namespacefiles.duration", duration));

        runContext.logger().info(
            "Loaded {} namespace files from '{}' in {}",
            matchedNamespaceFiles.size(),
            StringUtils.join(namespaces, ", "),
            DurationFormatUtils.formatDurationHMS(duration.toMillis())
        );
    }

    private static Path destinationPath(NamespaceFile namespaceFile, boolean folderPerNamespace) {
        return folderPerNamespace
            ? Path.of(namespaceFile.namespace() + "/" + namespaceFile.path())
            : Path.of(namespaceFile.path());
    }
}
