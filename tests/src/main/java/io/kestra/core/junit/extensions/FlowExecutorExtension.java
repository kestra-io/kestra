package io.kestra.core.junit.extensions;

import static io.kestra.core.junit.extensions.ExtensionUtils.loadFile;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.RunnerUtils;
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
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(KestraTestExtension.class);

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
            context = extensionContext.getRoot().getStore(NAMESPACE).get(ApplicationContext.class, ApplicationContext.class);

            if (context == null) {
                throw new IllegalStateException("No application context, to use '@LoadFlows' annotation, you need to add '@KestraTest'");
            }
        }

        ExecuteFlow executeFlow = getExecuteFlow(extensionContext);

        String path = executeFlow.value();
        URL url = getClass().getClassLoader().getResource(path);
        if (url == null) {
            throw new IllegalArgumentException("Unable to load flow: " + path);
        }
        LocalFlowRepositoryLoader repositoryLoader = context.getBean(LocalFlowRepositoryLoader.class);
        TestsUtils.loads(repositoryLoader, Objects.requireNonNull(url));
        YamlParser yamlParser = context.getBean(YamlParser.class);
        Flow flow = yamlParser.parse(Paths.get(url.toURI()).toFile(), Flow.class);
        RunnerUtils runnerUtils = context.getBean(RunnerUtils.class);
        return runnerUtils.runOne(null, flow.getNamespace(), flow.getId(), Duration.parse(executeFlow.timeout()));
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        ExecuteFlow executeFlow = getExecuteFlow(extensionContext);
        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);
        YamlParser yamlParser = context.getBean(YamlParser.class);
        String path = executeFlow.value();
        URL resource = loadFile(path);
        Flow loadedFlow = yamlParser.parse(Paths.get(resource.toURI()).toFile(), Flow.class);
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> Objects.equals(flow.getId(), loadedFlow.getId()))
            .forEach(flow -> flowRepository.delete(FlowWithSource.of(flow, "unused")));
    }

    private static ExecuteFlow getExecuteFlow(ExtensionContext extensionContext) {
        ExecuteFlow executeFlow = extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(ExecuteFlow.class);
        return executeFlow;
    }
}