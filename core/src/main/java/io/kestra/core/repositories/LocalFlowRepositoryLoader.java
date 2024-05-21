package io.kestra.core.repositories;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.PluginDefaultService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.validation.ConstraintViolationException;

import static io.kestra.core.utils.Rethrow.throwConsumer;

@Singleton
@Slf4j
public class LocalFlowRepositoryLoader {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private PluginDefaultService pluginDefaultService;

    public void load(URL basePath) throws IOException, URISyntaxException {
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

                this.load(tempDirectory.toFile());
            }
        } else {
            this.load(Paths.get(uri).toFile());
        }
    }


    public void load(File basePath) throws IOException {
        this.load(basePath, false);
    }

    public void load(File basePath, Boolean update) throws IOException {
        List<Path> list = Files.walk(basePath.toPath())
            .filter(YamlFlowParser::isValidExtension)
            .toList();

        for (Path file : list) {
            try {
                String flowSource = Files.readString(Path.of(file.toFile().getPath()), Charset.defaultCharset());
                Flow parse = yamlFlowParser.parse(file.toFile(), Flow.class);
                modelValidator.validate(parse);

                if (!update) {
                    this.createFlow(flowSource, parse);
                } else {
                    Optional<Flow> find = flowRepository.findById(parse.getTenantId(), parse.getNamespace(), parse.getId());

                    if (find.isEmpty()) {
                        this.createFlow(flowSource, parse);
                    } else {
                        this.udpateFlow(flowSource, parse, find.get());
                    }
                }
            } catch (ConstraintViolationException e) {
                log.warn("Unable to create flow {}", file, e);
            }
        }
    }

    private void createFlow(String flowSource, Flow parse) {
        flowRepository.create(
            parse,
            flowSource,
            parse
        );
        log.trace("Created flow {}.{}", parse.getNamespace(), parse.getId());
    }

    private void udpateFlow(String flowSource, Flow parse, Flow previous) {
        flowRepository.update(
            parse,
            previous,
            flowSource,
            parse
        );
        log.trace("Updated flow {}.{}", parse.getNamespace(), parse.getId());
    }
}
