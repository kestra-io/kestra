package io.kestra.core.junit.extensions;
import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import java.net.URL;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FlowLoaderExtension implements BeforeEachCallback, AfterEachCallback {
    private ApplicationContext applicationContext;

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (applicationContext == null) {
            extensionContext.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get())).put("test", "bla");

            applicationContext = extensionContext.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get()))
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

            TestsUtils.loads(loadFlows.tenantId(), repositoryLoader, resource);
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {

    }

    private static LoadFlows getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);
    }

}
