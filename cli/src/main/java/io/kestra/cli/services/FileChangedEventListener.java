package io.kestra.cli.services;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithPath;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.PluginDefaultService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import jakarta.annotation.Nullable;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
@Requires(property = "micronaut.io.watch.enabled", value = "true")
public class FileChangedEventListener {
    @Nullable
    private final FileWatchConfiguration fileWatchConfiguration;
    private final WatchService watchService;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private FlowService flowService;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private BroadcastQueueInterface<FlowInterface> flowQueue;

    private FlowFilesManager flowFilesManager;
    private Runnable cancellation;

    private final List<FlowWithPath> flows = new CopyOnWriteArrayList<>();

    @Inject
    public FileChangedEventListener(@Nullable FileWatchConfiguration fileWatchConfiguration, WatchService watchService) {
        this.fileWatchConfiguration = fileWatchConfiguration;
        this.watchService = watchService;
    }

    public void startListeningFromConfig() throws IOException, InterruptedException {
        if (fileWatchConfiguration != null && fileWatchConfiguration.isEnabled()) {
            this.flowFilesManager = new LocalFlowFileWatcher(flowRepositoryInterface, flowService);
            List<Path> paths = fileWatchConfiguration.getPaths();
            this.setup(paths);

            // Init existing flows not already in files
            flowRepositoryInterface.findAllForAllTenants().forEach(flow ->
            {
                flowToFile(flow, this.buildPath(flow));
                flows.add(FlowWithPath.of(flow, this.buildPath(flow).toString()));
            });

            // Listen for new/updated/deleted flows
            flowQueue.addListener(current ->
            {
                // If deleted
                if (current.isDeleted()) {
                    this.flows.stream().filter(flowWithPath -> flowWithPath.uidWithoutRevision().equals(current.uidWithoutRevision())).findFirst()
                        .ifPresent(flowWithPath ->
                        {
                            deleteFile(Paths.get(flowWithPath.getPath()));
                        });
                    this.flows.removeIf(flowWithPath -> flowWithPath.uidWithoutRevision().equals(current.uidWithoutRevision()));
                } else {
                    // if updated/created
                    Optional<FlowWithPath> flowWithPath = this.flows.stream().filter(fwp -> fwp.uidWithoutRevision().equals(current.uidWithoutRevision())).findFirst();
                    if (flowWithPath.isPresent()) {
                        flowToFile(current, Paths.get(flowWithPath.get().getPath()));
                    } else {
                        flows.add(FlowWithPath.of(current, this.buildPath(current).toString()));
                        flowToFile(current, null);
                    }
                }
            });

            this.startListening(paths);
        } else {
            log.info("File watching is disabled.");
        }
    }

    @PreDestroy
    void close() {
        cancellation.run();
    }

    public void startListening(List<Path> paths) throws IOException, InterruptedException {
        for (Path path : paths) {
            path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
        }

        WatchKey key;
        while ((key = watchService.take()) != null) {
            for (WatchEvent<?> watchEvent : key.pollEvents()) {
                try {
                    WatchEvent.Kind<?> kind = watchEvent.kind();
                    Path entry = (Path) watchEvent.context();

                    if (entry.toString().endsWith(".yml") || entry.toString().endsWith(".yaml")) {

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {

                            Path filePath = ((Path) key.watchable()).resolve(entry);
                            if (Files.isDirectory(filePath)) {
                                loadFlowsFromFolder(filePath);
                            } else {

                                try {
                                    String content = Files.readString(filePath, Charset.defaultCharset());

                                    Optional<FlowWithSource> flow = parseFlow(content, entry);
                                    if (flow.isPresent()) {
                                        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                            // Check if we already have a file with the given path
                                            if (flows.stream().anyMatch(flowWithPath -> flowWithPath.getPath().equals(filePath.toString()))) {
                                                Optional<FlowWithPath> previous = flows.stream().filter(flowWithPath -> flowWithPath.getPath().equals(filePath.toString())).findFirst();
                                                // Check if Flow from file has id/namespace updated
                                                if (previous.isPresent() && !previous.get().uidWithoutRevision().equals(flow.get().uidWithoutRevision())) {
                                                    flows.removeIf(flowWithPath -> flowWithPath.getPath().equals(filePath.toString()));
                                                    flowFilesManager.deleteFlow(previous.get().getTenantId(), previous.get().getNamespace(), previous.get().getId());
                                                    flows.add(FlowWithPath.of(flow.get(), filePath.toString()));
                                                }
                                            } else {
                                                flows.add(FlowWithPath.of(flow.get(), filePath.toString()));
                                            }
                                        } else {
                                            flows.add(FlowWithPath.of(flow.get(), filePath.toString()));
                                        }

                                        flowFilesManager.createOrUpdateFlow(GenericFlow.fromYaml(getTenantIdFromPath(filePath), content));
                                        log.info("Flow {} from file {} has been created or modified", flow.get().getId(), entry);
                                    }

                                } catch (NoSuchFileException e) {
                                    log.warn("File not found: {}, deleting it", entry, e);
                                    // the file might have been deleted while reading so if not found we try to delete the flow
                                    flows.stream()
                                        .filter(flow -> flow.getPath().equals(filePath.toString()))
                                        .findFirst()
                                        .ifPresent(throwConsumer(flowWithPath ->
                                        {
                                            flowFilesManager.deleteFlow(flowWithPath.getTenantId(), flowWithPath.getNamespace(), flowWithPath.getId());
                                            this.flows.removeIf(fwp -> fwp.uidWithoutRevision().equals(flowWithPath.uidWithoutRevision()));
                                        }));
                                } catch (IOException e) {
                                    log.error("Error reading file: {}", entry, e);
                                }
                            }
                        } else {
                            Path filePath = ((Path) key.watchable()).resolve(entry);
                            flows.stream()
                                .filter(flow -> flow.getPath().equals(filePath.toString()))
                                .findFirst()
                                .ifPresent(throwConsumer(flowWithPath ->
                                {
                                    flowFilesManager.deleteFlow(flowWithPath.getTenantId(), flowWithPath.getNamespace(), flowWithPath.getId());
                                    this.flows.removeIf(fwp -> fwp.uidWithoutRevision().equals(flowWithPath.uidWithoutRevision()));
                                }));
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error while watching flows", e);
                }
            }
            key.reset();
        }
    }

    private void setup(List<Path> folders) {
        for (Path folder : folders) {
            this.loadFlowsFromFolder(folder);
        }
    }

    private void loadFlowsFromFolder(Path folder) {
        try {
            Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                    if (!dir.equals(folder)) {
                        loadFlowsFromFolder(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".yml") || file.toString().endsWith(".yaml")) {
                        String content = Files.readString(file, Charset.defaultCharset());
                        Optional<FlowWithSource> flow = parseFlow(content, file);

                        if (flow.isPresent() && flows.stream().noneMatch(flowWithPath -> flowWithPath.uidWithoutRevision().equals(flow.get().uidWithoutRevision()))) {
                            flows.add(FlowWithPath.of(flow.get(), file.toString()));
                            try {
                                flowFilesManager.createOrUpdateFlow(GenericFlow.fromYaml(getTenantIdFromPath(file), content));
                            } catch (Exception e) {
                                log.error("Unexpected error while watching flows", e);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Loaded files from the folder {}", folder);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void flowToFile(FlowInterface flow, Path path) {
        Path defaultPath = path != null ? path : this.buildPath(flow);

        try {
            Files.writeString(defaultPath, flow.source());
            log.info("Flow {} has been written to file {}", flow.getId(), defaultPath);
        } catch (IOException e) {
            log.error("Error writing file: {}", defaultPath, e);
        }
    }

    private Optional<FlowWithSource> parseFlow(String content, Path entry) {
        try {
            FlowWithSource flow = pluginDefaultService.parseFlowWithAllDefaults(getTenantIdFromPath(entry), content, false);
            modelValidator.validate(flow);
            return Optional.of(flow);
        } catch (ConstraintViolationException | FlowProcessingException e) {
            log.warn("Error while parsing flow: {}", entry, e);
        }
        return Optional.empty();
    }

    private void deleteFile(Path file) {
        try {
            if (Files.deleteIfExists(file)) {
                log.info("File {} has been deleted successfully.", file);
            } else {
                log.warn("File {} does not exist.", file);
            }
        } catch (IOException e) {
            log.error("Error deleting file: {}", file, e);
        }
    }

    private Path buildPath(FlowInterface flow) {
        return fileWatchConfiguration.getPaths().getFirst().resolve(flow.uidWithoutRevision() + ".yml");
    }

    private String getTenantIdFromPath(Path path) {
        // FIXME there is probably a bug here when a tenant has '_' in its name,
        //  a valid tenant name is defined with following regex: "^[a-z0-9][a-z0-9_-]*"
        return path.getFileName().toString().split("_")[0];
    }
}
