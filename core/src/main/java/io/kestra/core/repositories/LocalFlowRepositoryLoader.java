package io.kestra.core.repositories;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.queues.QueueException;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Rethrow;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
public class LocalFlowRepositoryLoader {

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowService flowService;

    public List<FlowWithSource> load(URL basePath) throws IOException, URISyntaxException {
        return load(MAIN_TENANT, basePath);
    }

    public List<FlowWithSource> load(String tenantId, URL basePath) throws IOException, URISyntaxException {
        URI uri = basePath.toURI();

        if (uri.getScheme().equals("jar")) {
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                String substring = uri.toString().substring(uri.toString().indexOf("!") + 1);

                Path tempDirectory = Files.createTempDirectory("loader");

                for (Path path1 : fileSystem.getRootDirectories()) {
                    try (var files = Files.walk(path1)) {
                        files.filter(path -> Files.isRegularFile(path) && path.startsWith(substring))
                            .forEach(
                                throwConsumer(
                                    path -> FileUtils.copyURLToFile(
                                        path.toUri().toURL(),
                                        tempDirectory.resolve(path.toString().substring(1)).toFile()
                                    )
                                )
                            );
                    }
                }

                return this.load(tenantId, tempDirectory.toFile());
            }
        } else {
            return this.load(tenantId, Paths.get(uri).toFile());
        }
    }

    public List<FlowWithSource> load(File basePath) throws IOException {
        return load(MAIN_TENANT, basePath);
    }

    /**
     * Loads every flow found under {@code basePath} (a single file or a directory walked
     * recursively) into the repository, creating or updating each one.
     *
     * @return the persisted flows (with their assigned revision) that were successfully created or
     *         updated, in encounter order. Flows that fail to load are logged and skipped, so they
     *         are absent from the returned list.
     */
    public List<FlowWithSource> load(String tenantId, File basePath) throws IOException {
        Map<String, FlowInterface> flowByUidInRepository = flowRepository.findAllForAllTenants()
            .stream()
            .filter(flow -> tenantId.equals(flow.getTenantId()))
            .collect(Collectors.toMap(FlowId::uidWithoutRevision, Function.identity()));

        List<FlowWithSource> loaded = new ArrayList<>();
        try (Stream<Path> pathStream = Files.walk(basePath.toPath())) {
            pathStream.filter(YamlParser::isValidExtension)
                .forEach(Rethrow.throwConsumer(file ->
                {
                    try {
                        String source = Files.readString(Path.of(file.toFile().getPath()), Charset.defaultCharset());
                        GenericFlow parsed = GenericFlow.fromYaml(tenantId, source);

                        FlowInterface existing = flowByUidInRepository.get(parsed.uidWithoutRevision());

                        FlowWithSource persisted;
                        if (existing == null) {
                            persisted = flowService.create(parsed);
                            log.trace("Created flow {}.{}", parsed.getNamespace(), parsed.getId());
                        } else {
                            persisted = flowService.update(parsed, existing);
                            log.trace("Updated flow {}.{}", parsed.getNamespace(), parsed.getId());
                        }

                        loaded.add(persisted);
                    } catch (FlowProcessingException | ConstraintViolationException | QueueException e) {
                        log.warn("Unable to create flow {}", file, e);
                    }
                }));
        }

        return loaded;
    }
}
