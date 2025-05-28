package io.kestra.core.utils;

import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.kestra.core.models.tasks.FileExistComportment;
import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.runners.RunContext;
import io.kestra.core.storages.NamespaceFile;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.commons.lang3.time.StopWatch;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
public class NamespaceFilesUtils {
    @Inject
    private ExecutorsUtils executorsUtils;

    private ExecutorService executorService;

    @PostConstruct
    public void postConstruct() {
        this.executorService = executorsUtils.maxCachedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() * 4, 32), "namespace-file");
    }

    public void loadNamespaceFiles(
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

        List<NamespaceFile> matchedNamespaceFiles = new ArrayList<>();
        for (String namespace : namespaces) {
            List<NamespaceFile> files = runContext.storage()
                .namespace(namespace)
                .findAllFilesMatching(include, exclude);

          matchedNamespaceFiles.addAll(files);
        }

        Flux.fromIterable(matchedNamespaceFiles)
            .doOnNext(throwConsumer(namespaceFile -> {
                InputStream content = runContext.storage().getFile(namespaceFile.uri());
                runContext.workingDir().putFile(Path.of(namespaceFile.path()), content, fileExistComportment);
            }))
            .publishOn(Schedulers.fromExecutorService(executorService))
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
