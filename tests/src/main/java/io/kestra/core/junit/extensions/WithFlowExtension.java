package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.WithFlow;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.TestsUtils;
import java.net.URISyntaxException;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class WithFlowExtension extends AbstractFlowLoaderExtension implements
    ParameterResolver, AfterEachCallback {

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(WithFlowExtension.class);

    private static final String KEY_TENANT_ID = "tenantId";

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == Flow.class;
    }

    @SneakyThrows
    @Override
    public Object resolveParameter(ParameterContext parameterContext,
        ExtensionContext extensionContext) throws ParameterResolutionException {
        WithFlow withFlow = getLoadFlows(extensionContext);
        String tenantId = StringUtils.isNotBlank(withFlow.tenantId()) ? withFlow.tenantId() : TestsUtils.randomTenant();

        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        store.put(KEY_TENANT_ID, tenantId);

        loadFlows(extensionContext, tenantId, new String[] { withFlow.value() });
        return getFlow(withFlow.value()).toBuilder().tenantId(tenantId).build();
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
        String tenantId = store.get(KEY_TENANT_ID, String.class);

        if (StringUtils.isNotBlank(tenantId)) {
            WithFlow withFlow = getLoadFlows(extensionContext);
            deleteFlows(tenantId, new String[] { withFlow.value() });
        }
    }

    private static WithFlow getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(WithFlow.class);
    }

}
