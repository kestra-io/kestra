package org.kestra.core.repositories;

import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.serializers.YamlFlowParser;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolationException;

@Singleton
@Slf4j
public class LocalFlowRepositoryLoader {
    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private FlowRepositoryInterface flowRepository;

    public void load(URL basePath) throws IOException, URISyntaxException {
        this.load(new File(basePath.toURI()));
    }

    public void load(File basePath) throws IOException {
        List<Path> list = Files.walk(basePath.toPath())
            .filter(YamlFlowParser::isValidExtension)
            .collect(Collectors.toList());

        for (Path file: list) {
            Flow parse = yamlFlowParser.parse(file.toFile());

            try {
                flowRepository.create(parse);
            } catch (ConstraintViolationException e) {
                log.warn("Unable to create flow {}.{}", parse.getNamespace(), parse.getId(), e);
            }
        }
    }
}
