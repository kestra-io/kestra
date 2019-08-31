package org.floworc.core.repositories.types;

import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.RepositoryInterface;
import org.floworc.core.serializers.YamlFlowParser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LocalRepository implements RepositoryInterface {
    private File basePath;
    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

    public LocalRepository(File basePath) {
        this.basePath = basePath;
    }

    @Override
    public Optional<Flow> getFlowById(String id) {
        File file = new File(this.basePath, id + ".yaml");
        try {
            return Optional.of(yamlFlowParser.parse(file));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<Flow> getFlows() {
        try {
            return Files.list(this.basePath.toPath())
                .map(path -> this.getFlowById(path.toFile().getName()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
