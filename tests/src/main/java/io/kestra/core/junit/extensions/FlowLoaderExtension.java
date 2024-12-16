package io.kestra.core.junit.extensions;

import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;

import java.net.URL;

import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FlowLoaderExtension implements BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KestraTestExtension.class);

    private ApplicationContext applicationContext;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (applicationContext == null) {
            extensionContext.getRoot().getStore(NAMESPACE).put("test", "bla");

            applicationContext = extensionContext.getRoot().getStore(NAMESPACE).get(ApplicationContext.class, ApplicationContext.class);

            if (applicationContext == null) {
                throw new IllegalStateException("No application context, to use '@LoadFlows' annotation, you need to add '@KestraTest'");
            }
        }

        LocalFlowRepositoryLoader repositoryLoader = applicationContext.getBean(LocalFlowRepositoryLoader.class);

        LoadFlows loadFlows = extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);

        if (loadFlows != null) {
            for (String path : loadFlows.value()) {
                URL resource = getClass().getClassLoader().getResource(path);
                if (resource == null) {
                    throw new IllegalArgumentException("Unable to load flow: " + path);
                }

                TestsUtils.loads(repositoryLoader, resource);
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        FlowRepositoryInterface flowRepository = applicationContext.getBean(FlowRepositoryInterface.class);
        flowRepository.findAllForAllTenants().forEach(flow -> flowRepository.delete(
            FlowWithSource.of(flow, "unused")));
    }
}