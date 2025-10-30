package io.kestra.core.repositories;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.Rethrow;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

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
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
public class LocalFlowRepositoryLoader {

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private PluginDefaultService pluginDefaultService;

    public void load(URL basePath) throws IOException, URISyntaxException {
        load(MAIN_TENANT, basePath);
    }

    public void load(String tenantId, URL basePath) throws IOException, URISyntaxException {
        URI uri = basePath.toURI();

        if (uri.getScheme().equals("jar")) {
            try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                String substring = uri.toString().substring(uri.toString().indexOf("!") + 1);

                Path tempDirectory = Files.createTempDirectory("loader");

                for (Path path1 : fileSystem.getRootDirectories()) {
                    try (var files = Files.walk(path1)) {
                        files.filter(path -> Files.isRegularFile(path) && path.startsWith(substring))
                            .forEach(throwConsumer(path -> FileUtils.copyURLToFile(
                                path.toUri().toURL(),
                                tempDirectory.resolve(path.toString().substring(1)).toFile())
                            ));
                    }
                }

                this.load(tenantId, tempDirectory.toFile());
            }
        } else {
            this.load(tenantId, Paths.get(uri).toFile());
        }
    }

    public void load(File basePath) throws IOException {
        load(MAIN_TENANT, basePath);
    }

    public void load(String tenantId, File basePath) throws IOException {
        Map<String, FlowInterface> flowByUidInRepository = flowRepository.findAllForAllTenants()
            .stream()
            .filter(flow -> tenantId.equals(flow.getTenantId()))
            .collect(Collectors.toMap(FlowId::uidWithoutRevision, Function.identity()));

        try (Stream<Path> pathStream = Files.walk(basePath.toPath())) {
            pathStream.filter(YamlParser::isValidExtension)
                .forEach(Rethrow.throwConsumer(file -> {
                    try {
                        String source = Files.readString(Path.of(file.toFile().getPath()), Charset.defaultCharset());
                        GenericFlow parsed = GenericFlow.fromYaml(tenantId, source);

                        FlowWithSource flowWithSource = pluginDefaultService.injectAllDefaults(parsed, false);
                        modelValidator.validate(flowWithSource);

                        FlowInterface existing = flowByUidInRepository.get(flowWithSource.uidWithoutRevision());

                        if (existing == null) {
                            flowRepository.create(parsed);
                            log.trace("Created flow {}.{}", parsed.getNamespace(), parsed.getId());
                        } else {
                            flowRepository.update(parsed, existing);
                            log.trace("Updated flow {}.{}", parsed.getNamespace(), parsed.getId());
                        }
                    } catch (FlowProcessingException | ConstraintViolationException e) {
                        log.warn("Unable to create flow {}", file, e);
                    }
                }));
        }
    }
}
