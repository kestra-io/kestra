package org.floworc.repository.local;

import io.micronaut.context.annotation.Value;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.serializers.YamlFlowParser;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class LocalFlowRepository implements FlowRepositoryInterface {
    @Value("${floworc.repository.local.base-path}")
    private File basePath;

    private static final YamlFlowParser yamlFlowParser = new YamlFlowParser();

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
