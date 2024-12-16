package io.kestra.plugin.core.extensions;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import org.junit.jupiter.api.extension.*;

import java.util.Objects;

public class FlowLoaderExtension implements BeforeEachCallback, AfterEachCallback {

    private static ApplicationContext context;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (context == null) {
            context = ApplicationContext.run();
        }

        LocalFlowRepositoryLoader repositoryLoader = context.getBean(LocalFlowRepositoryLoader.class);

        LoadFlows loadFlows = extensionContext.getTestMethod()
                .orElseThrow()
                .getAnnotation(LoadFlows.class);

        if (loadFlows != null) {
            for (String path : loadFlows.value()) {
                TestsUtils.loads(repositoryLoader, Objects.requireNonNull(getClass().getClassLoader().getResource(path)));
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);
        flowRepository.findAllForAllTenants().forEach(flow -> flowRepository.delete(
            FlowWithSource.of(flow, "unused")));
    }
}