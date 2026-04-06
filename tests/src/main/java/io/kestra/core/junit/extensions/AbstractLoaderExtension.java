package io.kestra.core.junit.extensions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.extension.ExtensionContext;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.Pageable;
import io.micronaut.test.extensions.junit5.MicronautJunit5Extension;

import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;

public abstract class AbstractLoaderExtension {

    protected ApplicationContext context;

    protected void loadApplicationContext(ExtensionContext extensionContext) {
        if (context == null) {
            // Try KestraTestExtension namespace (used by @KestraTest)
            context = extensionContext.getRoot().getStore(
                ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get())
            ).get(ApplicationContext.class, ApplicationContext.class);

            // Fallback to MicronautJunit5Extension namespace (used by @MicronautTest)
            if (context == null) {
                context = extensionContext.getRoot().getStore(
                    ExtensionContext.Namespace.create(MicronautJunit5Extension.class)
                ).get(ApplicationContext.class, ApplicationContext.class);
            }

            if (context == null) {
                throw new IllegalStateException(
                    "No application context, to use this annotation you need to add '@KestraTest' or '@MicronautTest'"
                );
            }
        }
    }

    protected void loadFlows(ExtensionContext extensionContext, String tenantId, String[] paths)
        throws IOException, URISyntaxException {
        loadApplicationContext(extensionContext);

        LocalFlowRepositoryLoader repositoryLoader = context.getBean(
            LocalFlowRepositoryLoader.class
        );

        for (String path : paths) {
            URL resource = loadFile(path);

            TestsUtils.loads(tenantId, repositoryLoader, resource);
        }
    }

    protected void deleteFlows(String tenantId, String[] paths) throws URISyntaxException {
        if (!context.isRunning()) {
            return;
        }

        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);
        ExecutionRepositoryInterface executionRepository = context.getBean(ExecutionRepositoryInterface.class);

        Set<String> flowIds = new HashSet<>();
        for (String path : paths) {
            Flow flow = getFlow(path);
            flowIds.add(flow.getId());
        }
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> flowIds.contains(flow.getId()))
            .filter(flow -> tenantId.equals(flow.getTenantId()))
            .forEach(flow ->
            {
                flowRepository.deleteWithoutAcl(flow);
                executionRepository.findByFlowId(tenantId, flow.getNamespace(), flow.getId(), Pageable.UNPAGED)
                    .forEach(executionRepository::delete);
            });
    }

    protected static Flow getFlow(String path) throws URISyntaxException {
        URL resource = loadFile(path);
        Flow flow = YamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
        return flow;
    }
}
