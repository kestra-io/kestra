package io.kestra.webserver.services.expressions;

import java.io.IOException;
import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;
import io.kestra.core.services.KVStoreService;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KvExpressionContributor implements ExpressionContributor {
    private final KVStoreService kvStoreService;

    @Inject
    public KvExpressionContributor(KVStoreService kvStoreService) {
        this.kvStoreService = kvStoreService;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.KV_PAIRS;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) throws IOException {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        return kvStoreService.get(flow.getTenantId(), flow.getNamespace()).list().stream()
            .map(entry -> "kv('" + entry.key() + "')")
            .sorted()
            .toList();
    }
}
