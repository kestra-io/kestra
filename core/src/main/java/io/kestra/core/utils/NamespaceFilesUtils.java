package io.kestra.core.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.tasks.FileExistComportment;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.NamespaceFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public final class NamespaceFilesUtils {
    private static final int maxThreads = Math.max(Runtime.getRuntime().availableProcessors() * 4, 32);
    private static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(
        0,
        maxThreads,
        60L,
        TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        new ThreadFactoryBuilder().setNameFormat("namespace-files").build()
    );;

    private NamespaceFilesUtils() {
        // utility class pattern
    }

    public static void loadNamespaceFiles(
        RunContext runContext,
        NamespaceFiles namespaceFiles
    )
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

        List<NamespaceFile> matchedNamespaceFiles = new ArrayList<>();
        for (String namespace : namespaces) {
            List<NamespaceFile> files = runContext.storage()
                .namespace(namespace)
                .findAllFilesMatching(include, exclude);

          matchedNamespaceFiles.addAll(files);
        }

        // Use half of the available threads to avoid impacting concurrent tasks
        int parallelism = maxThreads / 2;
        Flux.fromIterable(matchedNamespaceFiles)
            .parallel(parallelism)
            .runOn(Schedulers.fromExecutorService(EXECUTOR_SERVICE))
            .doOnNext(throwConsumer(nsFile -> {
                InputStream content = runContext.storage().getFile(nsFile.uri());
                Path path = folderPerNamespace ?
                    Path.of(nsFile.namespace() + "/" + nsFile.path()) :
                    Path.of(nsFile.path());
                runContext.workingDir().putFile(path, content, fileExistComportment);
            }))
            .sequential()
            .blockLast();

        Duration duration = stopWatch.getDuration();

        runContext.metric(Counter.of("namespacefiles.count", matchedNamespaceFiles.size()));
        runContext.metric(Timer.of("namespacefiles.duration", duration));

        runContext.logger().info("Loaded {} namespace files from '{}' in {}",
            matchedNamespaceFiles.size(),
            StringUtils.join(namespaces, ", "),
            DurationFormatUtils.formatDurationHMS(duration.toMillis())
        );
    }
}
