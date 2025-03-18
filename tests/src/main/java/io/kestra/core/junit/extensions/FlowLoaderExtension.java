package io.kestra.core.junit.extensions;

import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;

import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FlowLoaderExtension implements BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
        KestraTestExtension.class);

    private ApplicationContext applicationContext;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (applicationContext == null) {
            extensionContext.getRoot().getStore(NAMESPACE).put("test", "bla");

            applicationContext = extensionContext.getRoot().getStore(NAMESPACE)
                .get(ApplicationContext.class, ApplicationContext.class);

            if (applicationContext == null) {
                throw new IllegalStateException(
                    "No application context, to use '@LoadFlows' annotation, you need to add '@KestraTest'");
            }
        }

        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(
            LocalFlowRepositoryLoader.class);

        LoadFlows loadFlows = getLoadFlows(extensionContext);

        for (String path : loadFlows.value()) {
            URL resource = loadFile(path);

            TestsUtils.loads(repositoryLoader, resource);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        FlowRepositoryInterface flowRepository = applicationContext.getBean(
            FlowRepositoryInterface.class);
        YamlParser yamlParser = applicationContext.getBean(YamlParser.class);
        Set<String> flowIds = new HashSet<>();
        for (String path : loadFlows.value()) {
            URL resource = loadFile(path);
            Flow flow = yamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
            flowIds.add(flow.getId());
        }
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> flowIds.contains(flow.getId()))
            .forEach(flow -> flowRepository.delete(FlowWithSource.of(flow, "unused")));
    }

    private static LoadFlows getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);
    }

}