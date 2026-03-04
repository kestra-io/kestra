package io.kestra.core.junit.extensions;

import io.kestra.core.junit.annotations.LoadFlows;
import java.net.URISyntaxException;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class FlowLoaderExtension extends AbstractFlowLoaderExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        loadFlows(extensionContext, loadFlows.tenantId(), loadFlows.value());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws URISyntaxException {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        deleteFlows(loadFlows.tenantId(), loadFlows.value());
    }

    private static LoadFlows getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);
    }

}
