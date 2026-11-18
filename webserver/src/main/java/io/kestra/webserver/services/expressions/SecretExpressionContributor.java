package io.kestra.webserver.services.expressions;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;
import io.kestra.webserver.services.SecretListingService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SecretExpressionContributor implements ExpressionContributor {
    private final SecretListingService<?> secretListingService;

    @Inject
    public SecretExpressionContributor(SecretListingService<?> secretListingService) {
        this.secretListingService = secretListingService;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.SECRETS;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) throws IOException {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        return secretListingService.listSecretKeys(flow.getTenantId(), flow.getNamespace()).values().stream()
            .flatMap(Set::stream)
            .distinct()
            .sorted()
            .map(key -> "secret('" + key + "')")
            .toList();
    }
}
