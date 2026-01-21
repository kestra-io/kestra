package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.utils.TestsUtils;
import java.net.URISyntaxException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class FlowLoaderWithTenantExtension extends AbstractFlowLoaderExtension implements
    ParameterResolver, AfterEachCallback {

    private String tenantId = TestsUtils.randomTenant();
    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == String.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        LoadFlowsWithTenant loadFlows = getLoadFlows(extensionContext);
        loadFlows(extensionContext, tenantId, loadFlows.value());
        return tenantId;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        LoadFlowsWithTenant loadFlows = getLoadFlows(extensionContext);
        deleteFlows(tenantId, loadFlows.value());
    }

    private static LoadFlowsWithTenant getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlowsWithTenant.class);
    }

}
