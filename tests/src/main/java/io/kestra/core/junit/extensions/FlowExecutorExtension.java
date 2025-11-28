package io.kestra.core.junit.extensions;

import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.context.ApplicationContext;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class FlowExecutorExtension implements AfterEachCallback, ParameterResolver {
    private ApplicationContext context;

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Execution.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        if (context == null) {
            context = extensionContext.getRoot().getStore(ExtensionContext.Namespace.create(KestraTestExtension.class, extensionContext.getTestClass().get())).get(ApplicationContext.class, ApplicationContext.class);

            if (context == null) {
                throw new IllegalStateException("No application context, to use '@LoadFlows' annotation, you need to add '@KestraTest'");
            }
        }

        ExecuteFlow executeFlow = getExecuteFlow(extensionContext);
        String tenantId = executeFlow.tenantId();

        String path = executeFlow.value();
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Unable to load flow: " + path);
        }
        LocalFlowRepositoryLoader repositoryLoader = context.getBean(LocalFlowRepositoryLoader.class);
        TestsUtils.loads(tenantId, repositoryLoader, Objects.requireNonNull(url));

        Flow flow = YamlParser.parse(Paths.get(url.toURI()).toFile(), Flow.class);
        TestRunnerUtils runnerUtils = context.getBean(TestRunnerUtils.class);
        return runnerUtils.runOne(tenantId, flow.getNamespace(), flow.getId(), Duration.parse(executeFlow.timeout()));
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        ExecuteFlow executeFlow = getExecuteFlow(extensionContext);
        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);

        String path = executeFlow.value();
        URL resource = loadFile(path);
        Flow loadedFlow = YamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> Objects.equals(flow.getId(), loadedFlow.getId()))
            .filter(flow -> Objects.equals(flow.getTenantId(), executeFlow.tenantId()))
            .forEach(flow -> flowRepository.delete(FlowWithSource.of(flow, "unused")));
    }

    private static ExecuteFlow getExecuteFlow(ExtensionContext extensionContext) {
        ExecuteFlow executeFlow = extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(ExecuteFlow.class);
        return executeFlow;
    }
}
