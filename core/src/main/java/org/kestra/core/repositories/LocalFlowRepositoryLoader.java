package org.kestra.core.repositories;

import org.kestra.core.models.flows.Flow;
import org.kestra.core.serializers.YamlFlowParser;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class LocalFlowRepositoryLoader {
    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

    @Inject
    private FlowRepositoryInterface flowRepository;

    public void load(URL basePath) throws IOException, URISyntaxException {
        this.load(new File(basePath.toURI()));
    }

    public void load(File basePath) throws IOException {
        List<Path> list = Files.walk(basePath.toPath())
            .filter(path -> com.google.common.io.Files.getFileExtension(path.toString()).equals("yaml"))
            .collect(Collectors.toList());

        for (Path file: list) {
            Flow parse = yamlFlowParser.parse(file.toFile());

            flowRepository.save(parse);
        }
    }
}
