package io.kestra.cli.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithPath;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.PluginDefaultService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Singleton
@Slf4j
@Requires(property = "micronaut.io.watch.enabled", value = "true")
public class FileChangedEventListener {
    @Nullable
    private final FileWatchConfiguration fileWatchConfiguration;
    @Nullable
    private final WatchService watchService;

    @Inject
    private FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private YamlParser yamlParser;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    protected FlowListenersInterface flowListeners;

    @Nullable
    @Value("${micronaut.io.watch.tenantId}")
    private String tenantId;

    FlowFilesManager flowFilesManager;

    private List<FlowWithPath> flows = new ArrayList<>();

    private boolean isStarted = false;


    @Inject
    public FileChangedEventListener(@Nullable FileWatchConfiguration fileWatchConfiguration, @Nullable WatchService watchService) {
        this.fileWatchConfiguration = fileWatchConfiguration;
        this.watchService = watchService;
    }

    public void startListeningFromConfig() throws IOException, InterruptedException {
        if (fileWatchConfiguration != null && fileWatchConfiguration.isEnabled()) {
            this.flowFilesManager = new LocalFlowFileWatcher(flowRepositoryInterface, pluginDefaultService);
            List<Path> paths = fileWatchConfiguration.getPaths();
            this.setup(paths);

            flowListeners.run();
            // Init existing flows not already in files
            flowListeners.listen(flows -> {
                if (!isStarted) {
                    for (FlowWithSource flow : flows) {
                        if (this.flows.stream().noneMatch(flowWithPath -> flowWithPath.uidWithoutRevision().equals(flow.uidWithoutRevision()))) {
                            flowToFile(flow, this.buildPath(flow));
                            this.flows.add(FlowWithPath.of(flow, this.buildPath(flow).toString()));
                        }
                    }
                    this.isStarted = true;
                }
            });

            // Listen for new/updated/deleted flows
            flowListeners.listen((current, previous) -> {
                // If deleted
                if (current.isDeleted()) {
                    this.flows.stream().filter(flowWithPath -> flowWithPath.uidWithoutRevision().equals(current.uidWithoutRevision())).findFirst()
                        .ifPresent(flowWithPath -> {
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

                                    Optional<Flow> flow = parseFlow(content, entry);
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

                                        flowFilesManager.createOrUpdateFlow(flow.get(), content);
                                        log.info("Flow {} from file {} has been created or modified", flow.get().getId(), entry);
                                    }

                                } catch (NoSuchFileException e) {
                                    log.error("File not found: {}", entry, e);
                                } catch (IOException e) {
                                    log.error("Error reading file: {}", entry, e);
                                }
                            }
                        } else {
                            Path filePath = ((Path) key.watchable()).resolve(entry);
                            flows.stream()
                                .filter(flow -> flow.getPath().equals(filePath.toString()))
                                .findFirst()
                                .ifPresent(flowWithPath -> {
                                    flowFilesManager.deleteFlow(flowWithPath.getTenantId(), flowWithPath.getNamespace(), flowWithPath.getId());
                                    this.flows.removeIf(fwp -> fwp.uidWithoutRevision().equals(flowWithPath.uidWithoutRevision()));
                                });
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
                        Optional<Flow> flow = parseFlow(content, file);

                        if (flow.isPresent() && flows.stream().noneMatch(flowWithPath -> flowWithPath.uidWithoutRevision().equals(flow.get().uidWithoutRevision()))) {
                            flows.add(FlowWithPath.of(flow.get(), file.toString()));
                            flowFilesManager.createOrUpdateFlow(flow.get(), content);
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

    private void flowToFile(FlowWithSource flow, Path path) {
        Path defaultPath = path != null ? path : this.buildPath(flow);

        try {
            Files.writeString(defaultPath, flow.getSource());
            log.info("Flow {} has been written to file {}", flow.getId(), defaultPath);
        } catch (IOException e) {
            log.error("Error writing file: {}", defaultPath, e);
        }
    }

    private Optional<Flow> parseFlow(String content, Path entry) {
        try {
            Flow flow = yamlParser.parse(content, Flow.class);
            FlowWithSource withPluginDefault = pluginDefaultService.injectDefaults(FlowWithSource.of(flow, content));
            modelValidator.validate(withPluginDefault);
            return Optional.of(flow);
        } catch (ConstraintViolationException e) {
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

    private Path buildPath(Flow flow) {
        return fileWatchConfiguration.getPaths().getFirst().resolve(flow.uidWithoutRevision() + ".yml");
    }
}
