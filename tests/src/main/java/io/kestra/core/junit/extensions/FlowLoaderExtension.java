package io.kestra.core.junit.extensions;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.kestra.core.junit.annotations.LoadFlows;

public class FlowLoaderExtension extends AbstractLoaderExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        loadFlows(extensionContext, loadFlows.tenantId(), loadFlows.value());
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        LoadFlows loadFlows = getLoadFlows(extensionContext);
        deleteFlows(loadFlows.tenantId(), loadFlows.value());
    }

    private static LoadFlows getLoadFlows(ExtensionContext extensionContext) {
        return extensionContext.getTestMethod()
            .orElseThrow()
            .getAnnotation(LoadFlows.class);
    }

}
