package io.kestra.core.junit.extensions;

import java.net.URL;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Objects;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.opentest4j.TestAbortedException;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.TestsUtils;

import lombok.SneakyThrows;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public class FlowExecutorExtension extends AbstractLoaderExtension implements AfterEachCallback, ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Execution.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        loadApplicationContext(extensionContext);
        if (!context.isRunning()) {
            throw new TestAbortedException("Application context is no longer running — skipping test");
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
        return runnerUtils.runOne(tenantId, flow.getNamespace(), flow.getId(), Duration.parse(executeFlow.timeout()), executeFlow.executionKind());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        if (!context.isRunning()) {
            return;
        }

        ExecuteFlow executeFlow = getExecuteFlow(extensionContext);
        FlowRepositoryInterface flowRepository = context.getBean(FlowRepositoryInterface.class);
        FlowService flowService = context.getBean(FlowService.class);

        String path = executeFlow.value();
        Flow loadedFlow = getFlow(path);
        flowRepository.findAllForAllTenants().stream()
            .filter(flow -> Objects.equals(flow.getId(), loadedFlow.getId()))
            .filter(flow -> Objects.equals(flow.getTenantId(), executeFlow.tenantId()))
            .forEach(throwConsumer(flow -> flowService.delete(FlowWithSource.of(flow, "unused"))));
    }

    private static ExecuteFlow getExecuteFlow(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(ExecuteFlow.class);
    }
}
