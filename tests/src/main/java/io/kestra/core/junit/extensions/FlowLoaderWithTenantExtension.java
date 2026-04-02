package io.kestra.core.junit.extensions;

import java.net.URISyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.utils.TestsUtils;

import lombok.SneakyThrows;

public class FlowLoaderWithTenantExtension extends AbstractLoaderExtension implements
    ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(WithFlowExtension.class);

    private static final String KEY_TENANT_ID = "tenantId";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == String.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        String tenantId = TestsUtils.randomTenant("test");
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put(KEY_TENANT_ID, tenantId);
        LoadFlowsWithTenant loadFlows = getLoadFlows(extensionContext);
        loadFlows(extensionContext, tenantId, loadFlows.value());
        return tenantId;
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String tenantId = store.get(KEY_TENANT_ID, String.class);

        if (StringUtils.isNotBlank(tenantId)) {
            LoadFlowsWithTenant loadFlows = getLoadFlows(extensionContext);
            deleteFlows(tenantId, loadFlows.value());
        }
    }

    private static LoadFlowsWithTenant getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlowsWithTenant.class);
    }

}
