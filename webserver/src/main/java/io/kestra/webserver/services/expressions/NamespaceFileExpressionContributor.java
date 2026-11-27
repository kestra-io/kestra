package io.kestra.webserver.services.expressions;

import java.io.IOException;
import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;
import io.kestra.core.storages.NamespaceFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NamespaceFileExpressionContributor implements ExpressionContributor {
    private final NamespaceFactory namespaceFactory;

    @Inject
    public NamespaceFileExpressionContributor(NamespaceFactory namespaceFactory) {
        this.namespaceFactory = namespaceFactory;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.NAMESPACE_FILES;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) throws IOException {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        return namespaceFactory.of(flow.getTenantId(), flow.getNamespace()).all().stream()
            .map(file -> "read('" + file.path() + "')")
            .sorted()
            .toList();
    }
}
